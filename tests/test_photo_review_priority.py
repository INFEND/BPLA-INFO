import unittest
from scripts.propose_unapproved_masks import review_priority


class ReviewPriorityTests(unittest.TestCase):
    def test_obstructed_photo_follows_unobstructed_candidate(self):
        clear = {"model_id": "a", "view": "top", "visual_review_notes": "Complete airframe visible."}
        blocked = {"model_id": "a", "view": "top", "visual_review_notes": "Hands overlap wing and center body."}
        self.assertLess(review_priority(clear), review_priority(blocked))

    def test_priority_does_not_change_publication_status(self):
        candidate = {"model_id": "a", "publication_status": "not_approved",
                     "visual_review_notes": "Complete airframe visible."}
        review_priority(candidate)
        self.assertEqual(candidate["publication_status"], "not_approved")


if __name__ == "__main__":
    unittest.main()
