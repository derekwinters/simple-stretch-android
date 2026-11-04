"""MkDocs hook for the store images page (docs/spec/store-listing.md, STORE-DOCS-001).

`docs/store/images.md` links each image by its real path, `../../fastlane/metadata/android/en-US/
images/...`, so github.com's Markdown view renders the committed files. Those paths are outside
`docs_dir`, so for the site this hook rewrites the prefix to `images/`, the committed symlink
`docs/store/images` to the same folder, in that page's Markdown only. MkDocs follows the symlink,
writes the files themselves into the built site, and `--strict` still fails on a missing image.
Nothing is copied or generated. Registered under `hooks:` in mkdocs.yml.
"""

PAGE = "store/images.md"
FASTLANE_PREFIX = "../../fastlane/metadata/android/en-US/images/"
SYMLINK_PREFIX = "images/"


def on_page_markdown(markdown, page, **kwargs):
    if page.file.src_uri != PAGE:
        return markdown
    return markdown.replace(FASTLANE_PREFIX, SYMLINK_PREFIX)
