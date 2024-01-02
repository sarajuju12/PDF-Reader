Juliette Xu

20897674 j556xu

Android API 30 SDK

Microsoft Windows 10.0.19045

Notes:
- For erasing functionality, user MUST CLICK ON the paint stroke they want to erase. Erase functionality DOES NOT work with mouse-down+drag (i.e. erase functionality is not implemented within MotionEvent.ACTION_MOVE, but is implemented within MotionEvent.ACTION_DOWN).
- For panning functionality, user must click on the RadioButton that has a pan icon, which allows user to pan with one finger.
- To navigate between pages, click on the back/forward buttons located at the very right of the toolbar.
- To undo and redo changes, click on the undo/redo buttons located near the left of the toolbar.
- Undo-Redo stacks are page-dependent, meaning that user can only undo/redo changes made on the current page they are on.
- Ensure that screen rotation is enabled within the emulator by swiping down from the top of the screen on the notification bar, and enabling the auto-rotate toggle.
- APK can be found at the top-level of the directory: app-debug.apk.
- Ideally, test this app using IntelliJ, using Pixel C with API 30 or higher.
- Data is saved and persists when application is paused. However, data is not saved when application is killed.
- Source of PNG images for toolbar buttons is @Flaticon.
- Source of PDFimage starter code is @sample-code/_old/Android/PanZoom.