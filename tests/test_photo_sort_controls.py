"""Regression checks for photo-only sorting and strict three-column list layout."""
import json
import pathlib
import unittest

ROOT=pathlib.Path(__file__).resolve().parents[1]
ACT=(ROOT/"app/src/main/java/dev/infend/bplainfo/MainActivity.java").read_text("utf-8")
PHOTOS=(ROOT/"app/src/main/java/dev/infend/bplainfo/PhotoLibrary.java").read_text("utf-8")

class PhotoSortAndAlignmentTests(unittest.TestCase):
    def test_photo_only_option_is_separate_and_toggleable(self):
        self.assertIn('String[] names=new String[sortNames.length+2]',ACT)
        self.assertIn('names[sortNames.length+1]=onlyWithPhotos?',ACT)
        self.assertIn('[С ФОТО]',ACT)
        self.assertIn('if(choice==sortNames.length+1)onlyWithPhotos=!onlyWithPhotos;',ACT)
        self.assertIn('else if(choice==sortNames.length)descending=!descending;',ACT)
        self.assertIn('if(onlyWithPhotos&&!hasAttachedPhoto(d))continue;',ACT)
        self.assertIn('onlyWithPhotos?"Для выбранных моделей пока нет фотографий"',ACT)

    def test_photo_filter_checks_local_and_physical_apk_not_remote_urls(self):
        self.assertIn('for(String image:d.galleryUris)',ACT)
        self.assertIn('isLocalPhotoUri(d.imageUri)',ACT)
        self.assertIn('PhotoLibrary.forDrone(this,d).isEmpty()',ACT)
        self.assertIn('value.startsWith("content://")',ACT)
        self.assertIn('value.startsWith("file://")',ACT)
        self.assertNotIn('value.startsWith("https://")',ACT)
        self.assertIn('getAssets().open(file)',PHOTOS)

    def test_list_star_removed_but_favorite_actions_preserved(self):
        card=ACT.split('private void renderCardBatch(',1)[1].split('private void stat(',1)[0]
        self.assertNotIn('TextView star=button(',card)
        self.assertNotIn('d.favorite=!d.favorite;',card)
        self.assertIn('d.favorite=!d.favorite;save();detail(d);',ACT)
        self.assertIn('onlyFavorites=!onlyFavorites;home();',ACT)

    def test_three_metrics_share_exact_column_gravity(self):
        self.assertIn('stat(stats,"АВТОНОМНОСТЬ",d.previewEndurance(),Gravity.START);',ACT)
        self.assertIn('stat(stats,"СКОРОСТЬ",d.previewSpeed(),Gravity.CENTER_HORIZONTAL);',ACT)
        self.assertIn('stat(stats,"ДАЛЬНОСТЬ",d.previewRange(),Gravity.END);',ACT)
        method=ACT.split('private void stat(',1)[1].split('private void save(',1)[0]
        self.assertIn('new LinearLayout.LayoutParams(0,-2,1)',method)
        self.assertIn('caption.setGravity(alignment|Gravity.CENTER_VERTICAL)',method)
        self.assertIn('amount.setGravity(alignment|Gravity.TOP)',method)
        self.assertIn('caption.setMinHeight(dp(18))',method)

    def test_embedded_photos_remain_physical_assets(self):
        data=json.loads((ROOT/"app/src/main/assets/bundled_photos.json").read_text("utf-8"))
        self.assertEqual(data["storage"],"apk-assets-only")
        for model in data["items"].values():
            for photo in model.get("files",[]):
                self.assertTrue((ROOT/"app/src/main/assets"/photo["file"]).is_file())

if __name__=="__main__":
    unittest.main()
