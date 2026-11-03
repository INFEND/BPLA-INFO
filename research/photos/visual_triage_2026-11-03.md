# Visual triage of three ArmyInform contact sheets · 2026-11-03

Scope: visually inspected original-image contact sheets for **Домаха (7)**,
**A1-СМ Фурия (7)** and **Cetus (6)**, produced from unchanged originals by
`scripts/make_photo_review_sheets.py`. These are 20 inspected candidate frames,
**not** 20 accepted photos. No cutouts, image modifications or APK imports
were performed during this review. For exact author, publisher, CC BY 4.0,
source and SHA-256 see `armyinform_review.json`.

## Домаха, lostarmour-pdf-2024-003

| Source filename | Visible obstacle / decision |
| --- | --- |
| 02-1-scaled.webp | Four people behind and partly holding the aircraft; hands occlude the body. Reject for automated complete-object cutout. |
| 03-1-scaled.webp | Left wing exits image boundary, hands across fuselage. Cannot recover full aircraft from pixels. |
| 04-2-scaled.webp | Operator's hands obscure center body; wings and rear remain visible. Needs detailed masking and occlusion audit. |
| 05-2-scaled.webp | Airborne aircraft seen from below; mostly unobstructed silhouette, but a thin launch cord crosses near the wing/body. **First non-generative masking candidate**. Preserve all original pixels of the aircraft. |
| 06-2-scaled.webp | Full planform against operator and trees; operator's arms overlap central body. Not clean without lost details. |
| 07-2-scaled.webp | Cropped detail of markings, no complete airframe. Reject as whole-aircraft gallery angle. |
| 08-1-scaled.webp | Detail with controller and hands, cropped aircraft. Reject as whole-aircraft gallery angle. |

## A1-СМ Фурия, lostarmour-pdf-2024-005

| Source filename | Visible obstacle / decision |
| --- | --- |
| 09-scaled.webp | Person behind full aircraft; hands cover central section. Not approved. |
| 10-1-scaled.webp | Close front view, both wing tips leave frame and hands cover wings. Reject for complete-airframe view. |
| 11-1-scaled.webp | Side view held by person; rear/wing obscured. Not approved. |
| 12-1-scaled.webp | Top view with entire planform and markings visible against soil; small operator gear/body near bottom overlaps rear area. **First precise pixel-preserving segmentation candidate**; compare every marking with original. |
| 13-1-scaled.webp | Similar angle to 09, operator overlaps body. Potential duplicate angle, not approved. |
| 14-1-scaled.webp | Operator obscures right rear and fuselage. Not approved. |
| 15-scaled.webp | Operator covers substantial central fuselage; not approved. |

## Cetus, lostarmour-pdf-2024-056

| Source filename | Visible obstacle / decision |
| --- | --- |
| 25-scaled.webp | Diagonal aircraft held by person; body and grass overlap airframe. Not approved. |
| 24-scaled.webp | Aircraft carried on shoulder, crossing person's head/hand. Not approved. |
| 23-scaled.webp | Distant aircraft and people, wing crosses operator. Reject for detailed cutout. |
| 27-scaled.webp | Front/top view, aircraft held across multiple people; fingers overlap wings. Not approved. |
| 28-scaled.webp | Wing/body carried on shoulder, occluded by operator. Not approved. |
| 29-scaled.webp | Front view across group, hands and grass obscure underside. Not approved. |

## Processing order and gates

1. Attempt non-generative pixel-preserving masks for **Домаха 05-2** and
   **Фурия 12-1**, retaining untouched originals and pixel-accurate markings.
   If segmentation introduces halos, destroys edges, or hides real components,
   reject the result and seek alternative frames.
2. Find unobstructed Cetus views from a separately verified licensed source;
   the six archived frames cannot yet demonstrate clean independent angles.
3. Obtain additional unobstructed angles for each model. A clean single view
   never completes a gallery; minimum three distinct verified views required.
4. Before any APK import, perform full-resolution visual comparison, verify
   model identity and rights to modify, supply attribution and modification
   notice, and run `scripts/batch_import_photos.py` dry-run then apply.
5. After import, verify bytes and decoding in the APK; Pillow is not Android
   BitmapFactory. Preserve the existing OTA signing certificate.

No visual approval flags were changed by this triage.
