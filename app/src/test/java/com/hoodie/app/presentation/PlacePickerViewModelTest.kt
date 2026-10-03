package com.hoodie.app.presentation

import com.hoodie.app.core.database.PlaceDao
import com.hoodie.app.core.database.PlaceEntity
import com.hoodie.app.core.geofence.GeofenceRegistrar
import com.hoodie.app.core.geofence.GeofenceRegistrationError
import com.hoodie.app.core.geofence.GeofenceRegistrationResult
import com.hoodie.app.core.location.AddressResult
import com.hoodie.app.core.location.AddressSearch
import com.hoodie.app.core.location.CurrentPosition
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.security.CoordinateCipher
import com.hoodie.app.core.time.FixedClock
import com.hoodie.app.data.repository.PlaceRepository
import com.hoodie.app.presentation.screens.places.PlacePickerState
import com.hoodie.app.presentation.screens.places.PlaceLoadState
import com.hoodie.app.presentation.screens.places.PlacePickerUiEvent
import com.hoodie.app.presentation.screens.places.PlacePickerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlacePickerViewModelTest {

    /** "Cifra" de mentira: só para o teste, nunca usada no app. */
    private class PlainCipher : CoordinateCipher {
        override fun encrypt(latitude: Double, longitude: Double) = "$latitude,$longitude"
        override fun decrypt(payload: String) = payload.split(',').let { it[0].toDouble() to it[1].toDouble() }
    }

    private class FakeDao : PlaceDao {
        val rows = MutableStateFlow<List<PlaceEntity>>(emptyList())
        var failWrites = false
        var failReads = false
        override fun observeAll() = rows
        override suspend fun getAll() = rows.value
        override suspend fun getById(id: Long): PlaceEntity? {
            if (failReads) error("falha de leitura")
            return rows.value.firstOrNull { it.id == id }
        }
        override suspend fun insert(place: PlaceEntity): Long {
            if (failWrites) error("disco cheio")
            val id = (rows.value.maxOfOrNull { it.id } ?: 0) + 1
            rows.value = rows.value + place.copy(id = id); return id
        }
        override suspend fun update(place: PlaceEntity) {
            if (failWrites) error("disco cheio")
            rows.value = rows.value.map { if (it.id == place.id) place else it }
        }
        override suspend fun delete(id: Long) { rows.value = rows.value.filterNot { it.id == id } }
        override suspend fun markVisited(id: Long, at: Long) = Unit
    }

    private class FakeSearch(var results: List<AddressResult>) : AddressSearch {
        var queries = 0
        override suspend fun search(query: String): List<AddressResult> { queries++; return results }
        override suspend fun reverse(latitude: Double, longitude: Double) = "Rua Teste, 1"
    }

    private class FakeRegistrar : GeofenceRegistrar {
        var calls = 0
        var mode = "ok" // ok | error | throw
        override val lastResult = MutableStateFlow<GeofenceRegistrationResult?>(null)
        override suspend fun registerAll(): GeofenceRegistrationResult {
            calls++
            return when (mode) {
                "throw" -> error("Play Services indisponível")
                "error" -> GeofenceRegistrationResult.failure(1, GeofenceRegistrationError.entries.first(), 0)
                else -> GeofenceRegistrationResult(1, 1, 0, null)
            }.also { lastResult.value = it }
        }
        override suspend fun clear() = Unit
    }

    private class FakePosition(var value: Pair<Double, Double>?) : CurrentPosition {
        override suspend fun current(): Pair<Double, Double>? = value
    }

    private lateinit var dao: FakeDao
    private lateinit var registrar: FakeRegistrar
    private lateinit var search: FakeSearch
    private lateinit var position: FakePosition

    private val paulista = AddressResult("Av. Paulista, 1000 - São Paulo", -23.5614, -46.6559)
    private val augusta = AddressResult("Rua Augusta, 500 - São Paulo", -23.5530, -46.6540)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        dao = FakeDao(); registrar = FakeRegistrar(); search = FakeSearch(emptyList()); position = FakePosition(null)
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = PlacePickerViewModel(search, PlaceRepository(dao, PlainCipher()), registrar, position, FixedClock(1_000))

    /** Coleta os eventos do ViewModel durante o teste. */
    private fun TestScope.events(vm: PlacePickerViewModel): MutableList<PlacePickerUiEvent> {
        val list = mutableListOf<PlacePickerUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.toList(list) }
        return list
    }

    private suspend fun insertWork() =
        dao.insert(PlaceEntity(name = "Escritório", type = PlaceType.WORK, encryptedCoordinates = "-23.0,-46.0", radiusMeters = 180f, confidence = 1f, createdAt = 0))

    // ── Fluxo completo da tela Novo Local ──

    @Test
    fun `fluxo de busca abre resultados escolhe recentraliza e habilita salvar`() = runTest {
        search.results = listOf(paulista, augusta)
        val vm = vm(); vm.init(PlaceType.WORK, null)
        assertFalse("sem ponto escolhido não salva", vm.state.value.canSave)

        vm.setQuery("Avenida Paulista 1000")
        vm.search()
        assertEquals(2, vm.state.value.results.size)
        assertFalse("resultado ainda não escolhido", vm.state.value.hasPoint)
        val key = vm.state.value.recenterKey

        vm.choose(paulista)
        val s = vm.state.value
        assertTrue("lista fecha ao escolher", s.results.isEmpty())
        assertEquals("mapa recebe recenterKey novo", key + 1, s.recenterKey)
        assertEquals(paulista.latitude, s.latitude, 1e-9)
        assertEquals(paulista.longitude, s.longitude, 1e-9)
        assertTrue(s.hasPoint)
        assertEquals("endereço selecionado aparece", paulista.label, s.address)
        assertTrue("Salvar habilita", s.canSave)
    }

    @Test
    fun `digitar o nome atualiza o estado`() = runTest {
        val vm = vm(); vm.init(PlaceType.WORK, null)
        vm.setName("Escritório Centro")
        assertEquals("Escritório Centro", vm.state.value.name)
    }

    // ── init ──

    @Test
    fun `novo lugar comeca com o nome do tipo e sem ponto`() = runTest {
        val vm = vm(); vm.init(PlaceType.GYM, null)
        val s = vm.state.value
        assertEquals("Academia", s.name)
        assertFalse(s.editing)
        assertFalse(s.canSave)
    }

    @Test
    fun `init de edicao preenche tipo, nome, raio, posicao e endereco`() = runTest {
        insertWork()
        val vm = vm(); vm.init(PlaceType.HOME, 1L)
        val s = vm.state.value
        assertTrue(s.editing)
        assertEquals(PlaceType.WORK, s.type)
        assertEquals("Escritório", s.name)
        assertEquals(180f, s.radius)
        assertEquals(-23.0, s.latitude, 1e-9); assertTrue(s.hasPoint)
        assertEquals("Rua Teste, 1", s.address)
        assertEquals(1, s.recenterKey)
    }

    // ── busca ──

    @Test
    fun `resultado unico ja escolhe o ponto e recentraliza`() = runTest {
        search.results = listOf(paulista)
        val vm = vm(); vm.init(PlaceType.HOME, null)
        vm.setQuery("Av Paulista 1000"); vm.search()
        val s = vm.state.value
        assertTrue(s.hasPoint)
        assertEquals(paulista.latitude, s.latitude, 1e-9)
        assertEquals(paulista.label, s.address)
        assertTrue(s.results.isEmpty())
        assertEquals(1, s.recenterKey)
        assertFalse(s.searching)
    }

    @Test
    fun `varios resultados ficam na lista ate a escolha`() = runTest {
        search.results = listOf(paulista, augusta)
        val vm = vm(); vm.init(PlaceType.HOME, null)
        vm.setQuery("São Paulo"); vm.search()
        assertEquals(2, vm.state.value.results.size)
        assertFalse(vm.state.value.hasPoint)
        vm.choose(augusta)
        assertEquals(augusta.longitude, vm.state.value.longitude, 1e-9)
        assertTrue(vm.state.value.results.isEmpty())
        assertEquals(augusta.label, vm.state.value.address)
    }

    @Test
    fun `sem resultados o erro fica na busca e nao permite salvar`() = runTest {
        val vm = vm(); vm.init(PlaceType.HOME, null)
        vm.setQuery("lugar que não existe"); vm.search()
        assertEquals(PlacePickerState.SEARCH_NOT_FOUND, vm.state.value.searchError)
        assertNull(vm.state.value.locationError); assertNull(vm.state.value.saveError)
        vm.save()
        assertTrue(dao.rows.value.isEmpty())
        // Digitar de novo limpa o erro da busca.
        vm.setQuery("outra")
        assertNull(vm.state.value.searchError)
    }

    @Test
    fun `busca vazia nao chama o servico`() = runTest {
        val vm = vm(); vm.init(PlaceType.HOME, null)
        vm.setQuery("   "); vm.search()
        assertEquals(0, search.queries)
    }

    // ── localização e mapa ──

    @Test
    fun `usar localizacao move o ponto e busca o endereco`() = runTest {
        position.value = -22.9 to -43.2
        val vm = vm(); vm.init(PlaceType.HOME, null)
        vm.useMyLocation()
        val s = vm.state.value
        assertFalse(s.locating)
        assertEquals(-22.9, s.latitude, 1e-9)
        assertTrue(s.hasPoint)
        assertEquals("Rua Teste, 1", s.address)
        assertFalse("loading da localização não é o da busca", s.searching)
    }

    @Test
    fun `erro na localizacao fica no mapa`() = runTest {
        val vm = vm(); vm.init(PlaceType.HOME, null)
        vm.useMyLocation()
        assertEquals(PlacePickerState.LOCATION_FAILED, vm.state.value.locationError)
        assertNull(vm.state.value.searchError)
        assertFalse(vm.state.value.locating)
        // Mexer no mapa resolve.
        vm.onCenterChanged(-23.1, -46.1)
        assertNull(vm.state.value.locationError)
    }

    @Test
    fun `mover o mapa muda o ponto e o endereco`() = runTest {
        val vm = vm(); vm.init(PlaceType.HOME, null)
        vm.onCenterChanged(-23.5, -46.5)
        val s = vm.state.value
        assertTrue(s.hasPoint)
        assertEquals(-46.5, s.longitude, 1e-9)
        assertEquals("Rua Teste, 1", s.address)
        assertEquals("arrastar não recentraliza", 0, s.recenterKey)
    }

    // ── detalhes ──

    @Test
    fun `trocar tipo acompanha o nome padrao mas nao um nome escolhido`() = runTest {
        val vm = vm(); vm.init(PlaceType.HOME, null)
        vm.setType(PlaceType.WORK)
        assertEquals("Trabalho", vm.state.value.name)
        vm.setName("Firma")
        vm.setType(PlaceType.SCHOOL)
        assertEquals(PlaceType.SCHOOL, vm.state.value.type)
        assertEquals("Firma", vm.state.value.name)
    }

    @Test
    fun `renomear limita o tamanho e alterar raio respeita os limites`() = runTest {
        val vm = vm(); vm.init(PlaceType.HOME, null)
        vm.setName("x".repeat(50))
        assertEquals(30, vm.state.value.name.length)
        vm.setRadius(150f); assertEquals(150f, vm.state.value.radius)
        vm.setRadius(10f); assertEquals(PlacePickerState.MIN_RADIUS, vm.state.value.radius)
        vm.setRadius(9_000f); assertEquals(PlacePickerState.MAX_RADIUS, vm.state.value.radius)
    }

    // ── salvar ──

    @Test
    fun `salvar novo cria o lugar, registra o geofence e emite Saved`() = runTest {
        search.results = listOf(paulista)
        val vm = vm(); val events = events(vm)
        vm.init(PlaceType.HOME, null)
        vm.setQuery("Av Paulista 1000"); vm.search()
        vm.setName("  Casa nova  ")
        vm.save()
        val saved = dao.rows.value.single()
        assertEquals(PlaceType.HOME, saved.type)
        assertEquals("Casa nova", saved.name)
        assertEquals("-23.5614,-46.6559", saved.encryptedCoordinates)
        assertEquals(1, registrar.calls)
        assertEquals(listOf<PlacePickerUiEvent>(PlacePickerUiEvent.Saved), events)
        assertFalse(vm.state.value.saving)
    }

    @Test
    fun `editar existente atualiza o mesmo lugar`() = runTest {
        insertWork()
        val vm = vm(); val events = events(vm)
        vm.init(PlaceType.WORK, 1L)
        vm.onCenterChanged(-23.5, -46.5)
        vm.setRadius(200f)
        vm.setName("Trabalho novo")
        vm.save()
        val row = dao.rows.value.single()
        assertEquals(1L, row.id)
        assertEquals("-23.5,-46.5", row.encryptedCoordinates)
        assertEquals(200f, row.radiusMeters)
        assertEquals("Trabalho novo", row.name)
        assertTrue(PlacePickerUiEvent.Saved in events)
    }

    @Test
    fun `erro no save mantem a tela aberta com o erro e permite tentar de novo`() = runTest {
        val vm = vm(); val events = events(vm)
        vm.init(PlaceType.HOME, null)
        vm.onCenterChanged(-23.5, -46.5)
        dao.failWrites = true
        vm.save()
        assertEquals(PlacePickerState.SAVE_FAILED, vm.state.value.saveError)
        assertFalse(vm.state.value.saving)
        assertTrue("não fecha a tela", events.isEmpty())
        assertEquals("geofence só depois de salvar", 0, registrar.calls)
        dao.failWrites = false
        vm.save()
        assertEquals(1, dao.rows.value.size)
        assertNull(vm.state.value.saveError)
        assertEquals(listOf<PlacePickerUiEvent>(PlacePickerUiEvent.Saved), events)
    }

    @Test
    fun `falha do geofence nao desfaz o lugar salvo e vira aviso`() = runTest {
        for (mode in listOf("error", "throw")) {
            dao.rows.value = emptyList()
            registrar.mode = mode
            val vm = vm(); val events = events(vm)
            vm.init(PlaceType.HOME, null)
            vm.onCenterChanged(-23.5, -46.5)
            vm.save()
            assertEquals("$mode: lugar continua salvo", 1, dao.rows.value.size)
            assertEquals(
                listOf(PlacePickerUiEvent.RetryGeofence, PlacePickerUiEvent.Saved),
                events,
            )
            assertNull(vm.state.value.saveError)
        }
    }

    @Test
    fun `nao salva duas vezes nem sem ponto`() = runTest {
        val vm = vm(); vm.init(PlaceType.HOME, null)
        vm.save()
        assertTrue(dao.rows.value.isEmpty())
        assertEquals(0, registrar.calls)
    }

    @Test
    fun `edicao de lugar inexistente nunca cria novo registro`() = runTest {
        val vm = vm()
        vm.init(PlaceType.HOME, 999)
        vm.onCenterChanged(-23.5, -46.5)
        vm.save()
        assertEquals(PlaceLoadState.NotFound, vm.state.value.loadState)
        assertFalse(vm.state.value.canSave)
        assertTrue(dao.rows.value.isEmpty())
        assertEquals(0, registrar.calls)
    }

    @Test
    fun `erro de leitura fica explicito e permite tentar novamente`() = runTest {
        dao.failReads = true
        val vm = vm()
        vm.init(PlaceType.HOME, 999)
        assertTrue(vm.state.value.loadState is PlaceLoadState.Error)
        assertFalse(vm.state.value.canSave)
        dao.failReads = false
        vm.reloadPlace()
        assertEquals(PlaceLoadState.NotFound, vm.state.value.loadState)
    }

    @Test
    fun `lugar removido depois de carregar nao vira insert`() = runTest {
        val id = dao.insert(PlaceEntity(name = "Casa", type = PlaceType.HOME,
            encryptedCoordinates = "-23.5,-46.5", radiusMeters = 100f, confidence = 1f, createdAt = 1))
        val vm = vm()
        vm.init(PlaceType.HOME, id)
        assertEquals(PlaceLoadState.Ready, vm.state.value.loadState)
        dao.delete(id)
        vm.save()
        assertEquals(PlaceLoadState.NotFound, vm.state.value.loadState)
        assertTrue(dao.rows.value.isEmpty())
        assertFalse(vm.state.value.saving)
        assertEquals(0, registrar.calls)
    }

    @Test
    fun `tentar geofence novamente nao repete a gravacao do lugar`() = runTest {
        registrar.mode = "error"
        val vm = vm(); vm.init(PlaceType.HOME, null)
        vm.onCenterChanged(-23.5, -46.5)
        vm.save()
        val saved = dao.rows.value.single()
        assertFalse(vm.retryGeofences())
        registrar.mode = "ok"
        assertTrue(vm.retryGeofences())
        assertEquals(listOf(saved), dao.rows.value)
        assertEquals(3, registrar.calls)
    }
}
