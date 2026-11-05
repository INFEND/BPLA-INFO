"""The thumbnail carousel must be edge-to-edge without deforming photos."""
import pathlib
import unittest

ROOT=pathlib.Path(__file__).resolve().parents[1]
SOURCE=(ROOT/"app/src/main/java/dev/infend/bplainfo/MainActivity.java").read_text("utf-8")
BEGIN=SOURCE.index('private FrameLayout addPhotoThumbnail(')
END=SOURCE.index('private void loadVisibleDocumentaryPhotos(',BEGIN)
GALLERY=SOURCE[BEGIN:END]

class PhotoGalleryLayoutTests(unittest.TestCase):
    def test_shared_edge_to_edge_rounded_frame(self):
        self.assertIn('FrameLayout frame=new FrameLayout(this)',GALLERY)
        self.assertIn('frame.setClipToOutline(true)',GALLERY)
        self.assertIn('frame.setForeground(outlined(Color.TRANSPARENT,t.stroke,14))',GALLERY)
        self.assertIn('position.rightMargin=dp(6)',GALLERY)
        self.assertIn('new LinearLayout.LayoutParams(dp(250),dp(190))',GALLERY)

    def test_both_bundled_and_local_photos_use_cover_without_distortion(self):
        self.assertEqual(GALLERY.count('FrameLayout frame=addPhotoThumbnail(gallery)'),2)
        self.assertEqual(GALLERY.count('ImageView photo=addThumbnailImage(frame)'),2)
        self.assertIn('photo.setScaleType(ImageView.ScaleType.CENTER_CROP)',GALLERY)
        self.assertIn('photo.setAdjustViewBounds(false)',GALLERY)
        self.assertIn('new FrameLayout.LayoutParams(',GALLERY)
        self.assertIn('ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT',GALLERY)
        self.assertNotIn('ImageView.ScaleType.FIT_CENTER',GALLERY)

    def test_no_captions_in_thumbnail_frames(self):
        self.assertNotIn('TextView photoStatus=',GALLERY)
        self.assertNotIn('photoStatus.setText',GALLERY)
        self.assertNotIn('frame.addView(photoStatus',GALLERY)
        self.assertIn('PhotoLibrary.into(this,p,photo,1350)',GALLERY)
        self.assertIn('frame.setOnClickListener(v->showDocumentaryPhoto(p))',GALLERY)
        self.assertIn('frame.setOnClickListener(v->showFullPhoto(path))',GALLERY)

    def test_fullscreen_has_no_metadata_controls(self):
        begin=SOURCE.index('private void showDocumentaryPhoto(')
        end=SOURCE.index('private void showFullPhoto(',begin)
        fullscreen=SOURCE[begin:end]
        self.assertIn('PhotoLibrary.into(',fullscreen)
        self.assertIn('ImageView.ScaleType.FIT_CENTER',fullscreen)
        for label in ('ИСТОЧНИК','ЛИЦЕНЗИЯ','СНИМОК ЗАГРУЖЕН',
                      'ОРИГИНАЛЬНАЯ ФОТОГРАФИЯ','item.author','item.license'):
            self.assertNotIn(label,fullscreen)
        # Provenance remains in APK metadata even though the viewer hides it.
        library=(ROOT/"app/src/main/java/dev/infend/bplainfo/PhotoLibrary.java").read_text("utf-8")
        self.assertIn('this.author=author',library)
        self.assertIn('this.license=license',library)
        self.assertIn('this.sha256=sha256',library)

if __name__=='__main__':
    unittest.main()
