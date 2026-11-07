import pathlib
import unittest

ROOT=pathlib.Path(__file__).resolve().parents[1]
SRC=ROOT/"app/src/main/java/dev/infend/bplainfo"
MAIN=(SRC/"MainActivity.java").read_text("utf-8")
SORT=(SRC/"SortSheet.java").read_text("utf-8")
DEDUP=(SRC/"CatalogDuplicates.java").read_text("utf-8")

class UserInterfaceRegression(unittest.TestCase):
    def test_infinite_scroll_replaces_load_button(self):
        self.assertNotIn("ПОКАЗАТЬ ЕЩЁ",MAIN)
        self.assertNotIn('footer.addView(button(',MAIN)
        self.assertIn('setOnScrollChangeListener(',MAIN)
        self.assertIn('loadNextCardBatchIfNearBottom()',MAIN)
        self.assertIn('catalogVisibleCount>=activeCardResults.size()',MAIN)
        self.assertIn('int offset=catalogVisibleCount;',MAIN)
        self.assertIn('renderCardBatch(current,currentResults,offset,epoch)',MAIN)
        self.assertIn('epoch!=cardRenderEpoch',MAIN)
        self.assertIn('private static final int CARD_BATCH_SIZE=18;',MAIN)
    def test_screen_transition_preserves_scroll(self):
        self.assertIn('this::openSettingsAnimated',MAIN)
        self.assertIn('private void openSettingsAnimated()',MAIN)
        self.assertIn('.withEndAction(()->screenTransitioning=false)',MAIN)
        self.assertIn('setContentView(homeRoot);',MAIN)
        self.assertIn('private void returnToHome()',MAIN)
        self.assertIn('homeScroll.getScrollY()',MAIN)
        self.assertIn('homeScroll.scrollTo(0,scrollPosition)',MAIN)
    def test_single_direction_arrow_with_toggle(self):
        self.assertIn('TextView direction=action(selected?(state.descending?"↓":"↑"):"↕"',SORT)
        self.assertIn('if(state.sortBy==item)state.descending=!state.descending;',SORT)
        self.assertIn('row.addView(direction);',SORT)
        self.assertNotIn('row.addView(action("↑"',SORT)
        self.assertNotIn('row.addView(action("↓"',SORT)
        self.assertIn('final int restoreY=position;',SORT)
    def test_exact_duplicates_keep_source_names_and_data(self):
        self.assertIn('private static final int REVISION=2;',DEDUP)
        self.assertIn('catalog_exact_duplicates_before_v2.json',DEDUP)
        self.assertIn('public static boolean sameVerifiedModel(',DEDUP)
        self.assertIn('!generated(first)||!generated(second)',DEDUP)
        self.assertIn('return leftMaker.isEmpty()||rightMaker.isEmpty()||leftMaker.equals(rightMaker);',DEDUP)
        self.assertIn('keeper.favorite|=other.favorite;',DEDUP)
        self.assertIn('keeper.galleryUris.add(uri);',DEDUP)
        self.assertIn('keeper.specRows.add(new String[]{row[0],row[1]});',DEDUP)
        self.assertIn('CatalogDuplicates.sameVerifiedModel(previous,d)',MAIN)
        self.assertIn('text(d.name().trim(),20,t.fg,true)',MAIN)
        self.assertNotIn('text(CardPresentation.name(d.name()),20,t.fg,true)',MAIN)

if __name__=="__main__":unittest.main()
