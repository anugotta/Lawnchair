package ch.deletescape.lawnchair.gestures.gestures

import ch.deletescape.lawnchair.gestures.Gesture
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.gestures.handlers.NotificationsOpenGestureHandler
import ch.deletescape.lawnchair.gestures.handlers.OpenDrawerGestureHandler

class VerticalSwipeGesture(controller: GestureController) : Gesture(controller) {

    override val isEnabled = true

    private val swipeUpHandler by controller.createHandlerPref("pref_gesture_swipe_up",
            OpenDrawerGestureHandler(controller.launcher, null))
    private val swipeDownHandler by controller.createHandlerPref("pref_gesture_swipe_down",
            NotificationsOpenGestureHandler(controller.launcher, null))

    val customSwipeUp get() = swipeUpHandler !is OpenDrawerGestureHandler
    val customSwipeDown get() = swipeDownHandler !is NotificationsOpenGestureHandler

    fun onSwipeUp() {
        swipeUpHandler.onGestureTrigger(controller)
    }

    fun onSwipeDown() {
        swipeDownHandler.onGestureTrigger(controller)
    }
}