package gov.nasa.gsfc.icesat2.icesat_2

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.DialogFragment

class NotificationsDialog : DialogFragment() {
    private lateinit var listener: MarkerSelectedFragment
    companion object {
        private const val TAG = "NotificationsDialog"
    }

    fun setListener(listener: MarkerSelectedFragment) {
        Log.d(TAG, "setListener - NotificationsDialog")
        this.listener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        Log.d(TAG, "onCreateDialog - NotificationsDialog")
        return activity?.let {
            val selectedItems = ArrayList<Int>() // Where we track the selected items
            val builder = AlertDialog.Builder(it)
            // Set the dialog title
            builder.setTitle(R.string.remindMe)
                // Specify the list array, the items to be selected by default (null for none),
                // and the listener through which to receive callbacks when items are selected
                .setMultiChoiceItems(R.array.notificationOptions, null
                ) { _, which, isChecked ->
                    if (isChecked) {
                        // If the user checked the item, add it to the selected items
                        selectedItems.add(which)
                    } else if (selectedItems.contains(which)) {
                        // Else, if the item is already in the array, remove it
                        selectedItems.remove(Integer.valueOf(which))
                    }
                }
                // Set the action buttons
                .setPositiveButton(R.string.ok
                ) { _, _ ->
                    // User clicked OK, so save the selectedItems results somewhere
                    // or return them to the component that opened the dialog
                    if (this::listener.isInitialized) {
                        listener.notificationOptionsChosen(selectedItems)
                    }
                }
                .setNegativeButton(R.string.cancel
                ) { _, _ ->
                }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}