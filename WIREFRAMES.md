# FolioScan — Low-Fidelity Wireframes

## Global Navigation Shell
- **Compact (Phone Portrait)**: Bottom `NavigationBar` with 4 destinations (`Home | Folders | Search | Settings`). Hidden on `Capture`, `Page Editor`, `Document Detail`, and `Vault`.
- **Medium / Expanded (Tablet / Landscape)**: Left `NavigationRail` with 4 destinations + fluid centered content (`widthIn(max = 600.dp)` or adaptive multi-column grids).
- **Selection Mode**: Top bar replaces title with `"N selected"` + `Move | Tag | Merge | Export | Delete`.

---

## 1. Home
```
+---------------------------------------+
| FolioScan        [Search] [Lock] [⋮]  |
+---------------------------------------+
| [Doc] [ID Card] [Passport] [BizCard]  |  <- Horizontal Mode Carousel
+---------------------------------------+
| Recent scans                          |
| +---------------+  +---------------+  |
| | [DOC]    [★]  |  | [ID]     [🔒] |  |
| |  Thumbnail    |  |  Thumbnail    |  |
| |               |  |               |  |
| | Receipt_01    |  | Driver Lic    |  |
| | Nov 16   2 pg |  | Nov 16   2 pg |  |
| +---------------+  +---------------+  |
|                        +-----------+  |
|                        | [+] Scan  |  |  <- Extended FAB
+------------------------+-----------+--+
| [Home]   [Folders]  [Search] [Settings]|
+---------------------------------------+
```
Empty State: Centered editorial illustration (`img_empty_scans`) + `"Scan your first document"` CTA button.

---

## 2. Capture (Full-screen, no nav bar)
```
+---------------------------------------+
| [X]     [Front 1/2]  [🔦] [⚡A] [⚙]   |  <- Top row + step pill
|        (  Hold steady… / Ready  )     |  <- QualityHint banner
|                                       |
|    ┌─────────────────────────────┐    |
|    │                             │    |  <- Animated corner guides
|    │     ID-1 / ID-3 / A4 Frame  │    |     (White -> Emerald Green
|    │                             │    |      when locked for 600ms)
|    └─────────────────────────────┘    |
|                                       |
|  [Gallery]      (( ◉ ))     [Auto]    |  <- Bottom reachability row
+---------------------------------------+
```
Review Step after capture: Full preview with `[Retake]` (left) and `[Use]` (right).

---

## 3. Page Editor (No nav bar)
```
+---------------------------------------+
| [←]  Editable Title     [↶] [↷] Done |
+---------------------------------------+
|                                       |
|        [ Pinch-Zoom Page Preview ]    |
|        (Crop / Sign / Redact live)    |
|                                       |
+---------------------------------------+
| [P1] [P2] [P3] [ + ]                  |  <- Reorderable thumbnail strip
+---------------------------------------+
| Crop | Rotate | Filter | Adjust | ... |  <- Scrollable ToolbarItem row
+---------------------------------------+
```
`Filter` and `Adjust` (brightness/contrast/sharpness sliders) open in a `ModalBottomSheet` over the toolbar.

---

## 4. Document Detail
```
+---------------------------------------+
| [←]  Passport_Scan        [☆]  [⋮]    |
+---------------------------------------+
|  Pages  |  Text (OCR)  | Fields | Info|  <- Fields tab on Passport/Card
+---------------------------------------+
|  (Pages: Vertical thumbnail grid)     |
|  (Text: Search-in-text + Copy btn)    |
|  (Fields: Editable FieldRow + ✓/⚠)    |
|  (Info: Created, size, folder, tags)  |
+---------------------------------------+
|  [ Share ]   [ Export ]   [ Edit ]    |  <- Sticky bottom action bar
+---------------------------------------+
```

---

## 5. Export Sheet (Modal Bottom Sheet)
```
+---------------------------------------+
|  Export Document                      |
|  [       PDF       |      JPG      ]  |
|  Quality (Balanced)           ~420 KB |
|  [────────●───────────────]           |
|  [ID Card Single A4] [Two Pages]      |
|  Password protect (AES-256)     [Off] |
|  Watermark                      [Off] |
|  Apply redaction black-out      [On ] |
|  [           Export PDF            ]  |
|  (After export: [Share] [Open])       |
+---------------------------------------+
```

---

## 6. Folders & Trash
```
+---------------------------------------+
| Folders                 [+ New folder]|
+---------------------------------------+
| Root > Receipts                       |  <- Nested breadcrumb
| [📁] Taxes 2026                   12  |
| [📁] Medical                       4  |
| [DocCard] Invoice_Oct                 |
|---------------------------------------|
| [🗑] Trash                         2  |  <- Pinned at bottom
+---------------------------------------+
| [Home]   [Folders]  [Search] [Settings]|
+---------------------------------------+
```

---

## 7. Search
```
+---------------------------------------+
| [🔍 Search OCR text, titles, tags…]   |  <- Auto-focused top input
| [DOCUMENT] [ID_CARD] [Last 30d] [📁]  |  <- Type/Date/Folder/Tag chips
+---------------------------------------+
| [Thumb]  Contract_Acme                |
|          "…total amount *invoice*…"   |  <- Highlighted FTS4 snippet
+---------------------------------------+
| [Home]   [Folders]  [Search] [Settings]|
+---------------------------------------+
```

---

## 8. Private Vault (`FLAG_SECURE`)
```
+---------------------------------------+
| [←]  Private Vault               [🔒] |
+---------------------------------------+
|          [Vault Shield Art]           |
|           Vault is locked             |
|       [ ••••••  PIN Input  ]          |
|       [  Unlock with PIN   ]          |
|       [   Use Biometric    ]          |
+---------------------------------------+
```
Unlocked state shows grid of hidden private IDs + `[Scan ID to Vault]` FAB.

---

## 9. Settings
```
+---------------------------------------+
| Settings                              |
+---------------------------------------+
| APPEARANCE: Theme chips, Dynamic color|
| SCANNING DEFAULTS: Limit, Filter, Auto|
| EXPORT DEFAULTS: PDF/JPG, Compression |
| SECURITY: App lock, Biometric, PIN,   |
|           Clipboard auto-clear timer  |
| STORAGE & BACKUP: Local ZIP Back/Rest |
| ABOUT: Offline notice, GMS fallback   |
| [         Clear all data            ] |  <- Red destructive button + confirm
+---------------------------------------+
```
