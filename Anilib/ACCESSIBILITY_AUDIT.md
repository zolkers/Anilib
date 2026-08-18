# Accessibility audit

The shared Compose shell is the single accessibility surface for Android and
desktop. Native browser, file, package, and installer dialogs remain owned by
their platforms.

| Requirement | Product contract | Automated or release evidence |
| --- | --- | --- |
| Screen reader | Text supplies names; actionable icon buttons have descriptions; decorative icons beside visible labels stay silent | compact and expanded semantic-tree review |
| Keyboard | Material controls retain focusability, traversal follows visual order, dialogs trap focus, Escape/back closes the active layer | desktop interaction pass |
| Focus | Route changes move to the new heading or primary action; destructive confirmation never reuses stale focus | route interaction pass |
| Contrast | Material light/dark palettes and AMOLED surfaces retain text/icon contrast; error text uses theme roles | compact/expanded light/dark capture review |
| Reduced motion | A durable `Reduce motion` setting disables reader page transitions | `SettingsTest` plus reader interaction pass |
| Large text | Small, standard, large, and extra-large typography multiply platform font scale without replacing density | compact/expanded large-text capture review |
| Touch target | Material buttons, rows, switches, navigation items, and sliders own the complete target rather than icons alone | Android interaction pass |

Release acceptance tests every audited route at compact 360×800 and expanded
1280×800 sizes, in keyboard-only and screen-reader modes, at extra-large text,
with reduced motion, and in both light and dark themes. Clipped actions,
unreachable focus, unlabeled nondecorative controls, spoken duplicates, contrast
failures, and motion that ignores the setting block promotion.
