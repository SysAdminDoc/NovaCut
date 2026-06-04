# Incoming Document Imports

NovaCut accepts non-media documents only through explicit Android intent
filters and a preview-first router. This keeps plugin, LUT, archive, and
timeline-interchange handoff separate from the media importer.

## Supported Files

| File family | Extensions | MIME families | Preview behavior |
|---|---|---|---|
| Project template | `.novacut-template` | `application/octet-stream` | Can be saved to Templates after the review dialog. |
| Effect pack | `.ncfx` | `application/octet-stream` | Validates through `EffectShareEngine`; apply from an editor clip. |
| Caption/text style pack | `.ncstyle` | `application/octet-stream` | Recognized, but installation is blocked until the style-pack loader lands. |
| LUT | `.cube`, `.3dl` | `text/plain`, `application/octet-stream` | Copies to cache and validates through `LutEngine`; apply from Color Grading. |
| OpenFX descriptor | `.ncfxd` | `application/json`, `application/octet-stream` | Validates with `OpenFxDescriptor`; metadata only. |
| Project archive | `.novacut`, `.zip` | `application/zip`, `application/octet-stream` | Validates through `ProjectArchive.importArchiveWithReport()` in a temporary preview directory. |
| Timeline interchange | `.otio`, `.fcpxml`, `.edl` | `application/json`, `application/xml`, `text/xml`, `text/plain`, `application/octet-stream` | Shows fidelity and current parser status from `TimelineImportEngine`; import remains blocked when the parser is stubbed. |

## Safety Rules

- `IncomingDocumentIntentParser` accepts only `content://` URIs.
- Shares require `FLAG_GRANT_READ_URI_PERMISSION`; `ACTION_VIEW` relies on the
  platform open grant.
- `*/*`, empty files, unsupported extensions, unknown MIME families, duplicate
  URIs, and files above per-kind byte caps are rejected before UI routing.
- The manifest registers specific JSON, XML, text, ZIP, and octet-stream
  document filters, never a catch-all `*/*` receiver.
- The Projects screen opens a review dialog that names the file kind, target
  action, parser/fidelity status, and warnings before any mutation.
- The only direct mutation enabled from the Projects review dialog is template
  import. Other recognized files are validation/report-only until opened from
  their existing editor surfaces.

## Verification

Keep the focused JVM coverage green when changing this route:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.novacut.editor.engine.IncomingDocumentIntentParserTest --tests com.novacut.editor.IncomingMediaManifestTest --no-daemon
```
