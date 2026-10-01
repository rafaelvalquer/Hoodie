package com.hoodie.app.presentation

import com.hoodie.app.core.database.PlaceDao
import com.hoodie.app.core.database.PlaceEntity
import com.hoodie.app.core.geofence.GeofenceRegistrar
import com.hoodie.app.core.geofence.GeofenceRegistrationResult
import com.hoodie.app.core.location.AddressResult
import com.hoodie.app.core.location.AddressSearch
import com.hoodie.app.core.location.CurrentPosition
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.security.CoordinateCipher
import com.hoodie.app.core.time.FixedClock
import com.hoodie.app.data.repository.PlaceRepository
import com.hoodie.app.presentation.screens.places.PlacePickerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
        override fun observeAll() = rows
        override suspend fun getAll() = rows.value
        override suspend fun getById(id: Long) = rows.value.firstOrNull { it.id == id }
        override suspend fun insert(place: PlaceEntity): Long {
            val id = (rows.value.maxOfOrNull { it.id } ?: 0) + 1
            rows.value = rows.value + place.copy(id = id); return id
        }
        override suspend fun update(place: PlaceEntity) { rows.value = rows.value.map { if (it.id == place.id) place else it } }
        override suspend fun delete(id: Long) { rows.value = rows.value.filterNot { it.id == id } }
        override suspend fun markVisited(id: Long, at: Long) = Unit
    }

    private class FakeSearch(private val results: List<AddressResult>) : AddressSearch {
        override suspend fun search(query: String) = results
        override suspend fun reverse(latitude: Double, longitude: Double) = "Rua Teste, 1"
    }

    private class CountingRegistrar : GeofenceRegistrar {
        var calls = 0
        override val lastResult = MutableStateFlow<GeofenceRegistrationResult?>(null)
        override suspend fun registerAll(): GeofenceRegistrationResult {
            calls++
            return GeofenceRegistrationResult(1, 1, 0, null).also { lastResult.value = it }
        }
        override suspend fun clear() = Unit
    }

    private val noPosition = object : CurrentPosition { override suspend fun current(): Pair<Double, Double>? = null }

    private lateinit var dao: FakeDao
    private lateinit var registrar: CountingRegistrar

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        dao = FakeDao(); registrar = CountingRegistrar()
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(results: List<AddressResult>) =
        PlacePickerViewModel(FakeSearch(results), PlaceRepository(dao, PlainCipher()), registrar, noPosition, FixedClock(1_000))

    @Test
    fun `buscar endereco e salvar cria a Casa nas coordenadas do resultado`() = runTest {
        val home = AddressResult("Av. Paulista, 1000 - São Paulo", -23.5614, -46.6559)
        val vm = vm(listOf(home))
        vm.init(PlaceType.HOME, null)
        vm.setQuery("Av Paulista 1000")
        vm.search()
        val s = vm.state.value
        assertTrue(s.hasPoint)
        assertEquals(home.latitude, s.latitude, 1e-9)
        vm.save()
        val saved = dao.rows.value.single()
        assertEquals(PlaceType.HOME, saved.type)
        assertEquals("-23.5614,-46.6559", saved.encryptedCoordinates)
        assertEquals(1, registrar.calls)
        assertTrue(vm.state.value.done)
    }

    @Test
    fun `arrastar o mapa muda o ponto e editar atualiza o lugar existente`() = runTest {
        dao.insert(PlaceEntity(name = "Trabalho", type = PlaceType.WORK, encryptedCoordinates = "-23.0,-46.0", radiusMeters = 150f, confidence = 1f, createdAt = 0))
        val vm = vm(emptyList())
        vm.init(PlaceType.WORK, 1L)
        assertEquals(-23.0, vm.state.value.latitude, 1e-9)
        vm.onCenterChanged(-23.5, -46.5)
        assertEquals("Rua Teste, 1", vm.state.value.address)
        vm.setRadius(200f)
        vm.save()
        val row = dao.rows.value.single()
        assertEquals("-23.5,-46.5", row.encryptedCoordinates)
        assertEquals(200f, row.radiusMeters)
    }

    @Test
    fun `sem resultados mostra mensagem e nao permite salvar`() = runTest {
        val vm = vm(emptyList())
        vm.init(PlaceType.HOME, null)
        vm.setQuery("lugar que não existe")
        vm.search()
        assertNotNull(vm.state.value.message)
        assertFalse(vm.state.value.hasPoint)
        vm.save()
        assertTrue(dao.rows.value.isEmpty())
    }
}
