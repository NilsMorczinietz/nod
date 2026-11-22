; Nod installer texts. Tauri's NSIS template includes this file (bundle.windows.nsis.installerHooks)
; before it inserts the pages, so these defines are picked up by the welcome and finish pages.
; Passive and silent installs (used by the updater) skip both pages, so they are unaffected.

!define MUI_WELCOMEPAGE_TITLE "Welcome to Nod"
!define MUI_WELCOMEPAGE_TEXT "Nod shows Claude Code's requests at the top of your screen and on your phone, so you can approve them from anywhere.$\r$\n$\r$\nClick Next to continue."

!define MUI_FINISHPAGE_TITLE "Nod is installed"
!define MUI_FINISHPAGE_TEXT "Nod waits in the tray and appears when Claude Code needs you.$\r$\n$\r$\nTo connect it, run nod pair on the machine with Claude Code."
; The run checkbox is checked by default.
!define MUI_FINISHPAGE_RUN_TEXT "Start Nod"
