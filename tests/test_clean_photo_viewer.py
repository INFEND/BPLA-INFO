"""Fullscreen bundled-photo viewer must not display developer provenance UI.

Attribution, source URLs and license remain in offline data for auditing.
"""
import pathlib
import unittest

MAIN = pathlib.Path(__file__).resolve().parents[1] / "app/src/main/java/dev/infend/bplainfo/MainActivity.java"

class PhotoViewerPresentationTests(unittest.TestCase):
    def test_fullscreen_viewer_is_photo_only(self):
        java = MAIN.read_text(encoding="utf-8")
        begin = java.index("private void showDocumentaryPhoto(")
        end = java.index("private void showFullPhoto(", begin)
        viewer = java[begin:end]
        self.assertIn("PhotoLibrary.into(", viewer)
        self.assertIn("overlay::dismiss", viewer.replace("v->overlay.dismiss()", "overlay::dismiss"))
        for forbidden in (
            "appendPhotoReferences(", "ИСТОЧНИК", "ЛИЦЕНЗИЯ", "СНИМОК ЗАГРУЖЕН",
            "ОТКРЫТИЕ ФОТО", "attribution", "item.author", "item.license", "item.source"
        ):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, viewer)
        self.assertIn("FrameLayout", viewer)
        self.assertIn("FIT_CENTER", viewer)

if __name__ == "__main__":
    unittest.main()
