package id.apwdevs.simpleble

import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog

object Utils {
    fun displayDialogRequest(context: Context, title: String, message: String, onAction: () -> Unit){
        AlertDialog.Builder(context).apply {
            setTitle(title)
            setMessage(message)
            setCancelable(true)
            setPositiveButton("OKAY") { dialog, _ ->
                dialog.dismiss()
                onAction()
            }
        }.show()
    }
}