# Badge pictures

The pictures that `badges.json` names in its `"image"` field. The app downloads
`https://raw.githubusercontent.com/pikmis/lotustok/main/badges/<image>` the first
time a badge is drawn and keeps it on the phone.

- PNG, square, transparent background, at most 4 MB (256x256 is plenty).
- Use a plain file name: letters, digits, dot, dash.
- A badge with no `"image"` is drawn with the mod's own note, painted with its `"colour"`.

To give a badge: add the account's numeric TikTok id (the one in *Settings > Account*)
to the `"users"` list of the badge in `../badges.json`, commit and push. Phones pick it up
within five minutes (GitHub caches raw files for that long).
