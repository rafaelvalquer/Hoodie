# NPC Art Standard V2

## Purpose

Ambient characters share the Hoodie project's pixel language and body pose contract. `CharacterPose` carries reusable body state; `CharacterStyle` composes a species silhouette, an independent outfit, a palette and a scale; `CharacterPainter` renders the 48×72 logical canvas and returns common anchors. Scenes choose registered characters and behavior profiles, while `NpcRenderer` positions finished frames and delegates speech bubbles.

## Visual rules

- Use a 48×72 logical canvas and nearest-neighbor scaling.
- Keep a crisp dark outline, stepped forms, restrained highlights and up to ten principal palette colors.
- Share arms, legs, eyes, pose and anchors. Species may alter head, ears, muzzle, beak, tail and motion profile.
- Keep facial anatomy in the species style: eye spacing and markings such as a raccoon mask are provided by `SpeciesStyle`; the shared character painter must not branch on species ID strings.
- Resolve mouth animation after the species muzzle: broad canine muzzles use a compact jaw opening and tongue accent, while duck expressions stay inside the beak silhouette.
- Apply `SpeciesMotionProfile` to rendered step width, arm swing, body bob, head lag and tail motion; declared profile values must visibly affect the frame.
- Keep shared mammal hand masks identical to the Hoodie DOWN-paw's chamfered 6×6 silhouette; species colors and highlights may differ. Duck wings keep their species override.
- Give shared mammal paws the same rounded, roughly six-pixel-deep silhouette as Hoodie, with a restrained top highlight, toe separations and a darker far-side paw. Species such as Duck may override the foot shape.
- Keep outfit drawing in `OutfitPainter`. Suit, student, casual, sport and commuter looks need a shaded silhouette and a few legible construction details.
- Render outfits with the character's facing: rear views use back seams, panels or backpack straps, and must not repeat front-only details such as a shirt panel, tie, zipper or pocket.
- Side views use a narrower torso, overlapping hips and one visible near-side arm, with species-appropriate profile details; front-only shirt panels and ties must not span the profile.
- Do not render eyes, muzzle or mouth expressions in rear-facing views.
- Treat formal footwear as part of the suit, layering polished shoes over the species foot silhouette without introducing extra palette colors.
- Keep feet, head, both hands, mouth and back anchors inside the canvas. Scene placement aligns the feet anchor to the ground line.
- Keep seated NPC head, torso, arms and carried props on a shared five-pixel downward shift; feet remain aligned to the same ground anchor.
- Keep tails within the 48px horizontal frame at maximum wag; move their hidden root under the torso so the visible length stays connected without clipping. In profile, draw the tail toward the rear silhouette, opposite the muzzle.
- Draw carried props at the hand anchors. In particular, the `SIT_PHONE` clip must show the phone in the character's hand.
- Match ambient behavior to the scene: restaurant guests use the seated eating clip and fork; transit riders may use the phone or nap.
- Keep speech bubble glyphs and placement outside the body painter.
- In Pixel Lab comparisons, render Hoodie and NPC from the same pose, expression, posture override and side orientation.

## Character benchmark

`BULLDOG_EXEC` is the NPC benchmark. It uses a wide head/body, compact side-floppy ears, heavy brow, pronounced broad muzzle, centered nose/cheeks and a layered suit with shirt, lapels, tie and trousers. His walk should read as heavy and short-strided. NPC WALK samples the Hoodie `AnimationId.WALK` clip's eight pose phases and their individual durations; IDLE, LOOK, TALK, PHONE_SIT and NAP_SIT also sample their established clips. NPC wrappers only adjust direction, posture, blink timing and species motion weight. The office path brings him in from the left, pauses to look and speak, then continues off screen.

The Bulldog walk regression samples the start, midpoint and last millisecond of every timed phase; at least one paw must remain grounded at each sample while lower-body pixels still animate across the cycle.

Rabbit's `walkBob = 2` adds a one-pixel alternating rebound at the down/pass phases. Profiles at the shared baseline preserve the source clip's exact bob; the Rabbit's springier step is added without changing phase timing or foot contact.

`CharacterGeometry.resolveHoodie` supplies the shared geometry contract with the Hoodie renderer's exact legacy offsets for bob, sitting and lift. The pixel regression suite remains the guard while more body drawing migrates into shared painters.

The benchmark must belong beside Hoodie: matching outline weight, pixel density, shadow detail, eye language, hands, feet, garment volume and compatible overall height. Compare the character gallery outputs when revising style. Existing Hoodie procedural hashes and animation clip hashes remain the compatibility gate; do not update them to mask differences.

`NpcArtConsistencyTest` also compares visible height directly: Bulldog must remain within three logical pixels of Hoodie in front idle, side walk and side talk poses.

Across each registered NPC, animation and facing, the shared visual consistency check keeps at least 64 outline-color pixels and an outline-color coverage between 8% and 42% of occupied pixels. This is a regression guard for outline visibility and pixel density, not a substitute for visual approval of line weight.

In front idle, the same test compares palette-derived head width/height and torso height so a matching full-frame height cannot hide a disproportionate head or trunk.

The NPC SHA comparison and review gallery use the same front-IDLE pose at 400 ms with seed 0. The blink window is 0–129 ms in each 3,900 ms cycle, so this sample keeps the eyes open and makes the reviewed image identical to the frame guarded by the golden digest.

## Registry and review

`NpcCharacterRegistry` is the catalog for Bulldog, Dog, Rabbit, Mouse, Duck, Raccoon and Cat identities. Add new identity records there and keep scene-specific behavior and speech in NPC definitions. The Pixel Lab NPC tab renders Hoodie and a selected registered character side by side, with animation, facing and anchor inspection.

Review all species in the common animation set (IDLE, WALK, LOOK, TALK, SIT_PHONE, SIT_EAT and SIT_SLEEP) and front/side/back views. Also inspect NPCs integrated into each ambient scene. The automated consistency tests check canvas, outline, stable rendering and in-canvas anchors. Visual approval remains a separate art review; test hashes are regression signals, not a substitute for seeing proportions and silhouette.

`NpcArtReviewPreviewTest.exportReferenceSpeciesAcrossUniversalAnimationsAndViews` exports one contact sheet per animation under `app/build/pixel-preview/npc-art-review/`. Each sheet has seven rows in this order: Bulldog, Dog, Rabbit, Mouse, Duck, Raccoon, Cat; columns are front, side, back. Every cell compares Hoodie on the left with that species on the right.

To regenerate the benchmark review images, run `:app:testDebugUnitTest --tests com.hoodie.app.pixel.NpcArtReviewPreviewTest`. The test exports `bulldog_idle_front`, `bulldog_walk_side`, `bulldog_talk`, `bulldog_suit_day` and `bulldog-office-scene` under `app/build/pixel-preview/npc-art-review/`, plus the consolidated `bulldog-golden-master.png`. It verifies that each PNG exists and has the expected dimensions. Run `:app:testDebugUnitTest --tests com.hoodie.app.pixel.NpcArtGoldenTest` to export all seven Hoodie-versus-species comparisons and the candidate `npc-art-v2-current.sha256` report under that same directory; this comparison reports all changed digests together and leaves the approved resource untouched.

## Scope

This is a rendering and animation change. It must not add Room migrations or alter places, user context or mobility models.
