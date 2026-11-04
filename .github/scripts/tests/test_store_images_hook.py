"""Unit tests for the MkDocs hook that maps the store images page onto the symlink (STORE-DOCS-001).

`docs/store/images.md` links each image by its real path, `../../fastlane/metadata/android/en-US/
images/...`, so github.com renders it. On the MkDocs site the hook rewrites that prefix to
`images/`, the committed symlink to the same folder, for that page only.
"""

import importlib.util
import os
import unittest
from types import SimpleNamespace

REPO_ROOT = os.path.dirname(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
)
_spec = importlib.util.spec_from_file_location(
    "store_images", os.path.join(REPO_ROOT, "hooks", "store_images.py")
)
store_images = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(store_images)


def page(src_uri):
    return SimpleNamespace(file=SimpleNamespace(src_uri=src_uri))


class StoreImagesHookTest(unittest.TestCase):
    # STORE-DOCS-001: the fastlane paths become the symlinked `images/` paths on the site.
    def test_rewrites_fastlane_paths_on_the_store_images_page(self):
        markdown = (
            "![Home](../../fastlane/metadata/android/en-US/images/phoneScreenshots/1_home.png)\n"
            "![Icon](../../fastlane/metadata/android/en-US/images/icon.png)\n"
        )
        result = store_images.on_page_markdown(markdown, page=page("store/images.md"))
        self.assertEqual(
            result,
            "![Home](images/phoneScreenshots/1_home.png)\n![Icon](images/icon.png)\n",
        )

    # STORE-DOCS-001: no other page is touched.
    def test_leaves_other_pages_unchanged(self):
        markdown = "`../../fastlane/metadata/android/en-US/images/icon.png`\n"
        result = store_images.on_page_markdown(markdown, page=page("store/captions.md"))
        self.assertEqual(result, markdown)

    # STORE-DOCS-001 invariant: the rewrite only changes the path prefix, so a renamed or
    # missing file still reaches MkDocs' strict link check under its own name.
    def test_keeps_the_file_name(self):
        markdown = "![x](../../fastlane/metadata/android/en-US/images/missing.png)"
        result = store_images.on_page_markdown(markdown, page=page("store/images.md"))
        self.assertEqual(result, "![x](images/missing.png)")


if __name__ == "__main__":
    unittest.main()
