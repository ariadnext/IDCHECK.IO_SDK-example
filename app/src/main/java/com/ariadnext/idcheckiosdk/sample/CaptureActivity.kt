package com.ariadnext.idcheckiosdk.sample

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ariadnext.idcheckio.sdk.bean.*
import com.ariadnext.idcheckio.sdk.interfaces.ErrorMsg
import com.ariadnext.idcheckio.sdk.interfaces.IdcheckioInteraction
import com.ariadnext.idcheckio.sdk.interfaces.IdcheckioInteractionInterface
import com.ariadnext.idcheckio.sdk.interfaces.result.Document
import com.ariadnext.idcheckio.sdk.interfaces.result.IdcheckioResult
import kotlinx.android.synthetic.main.activity_capture.*


class CaptureActivity : AppCompatActivity(), IdcheckioInteractionInterface {

    companion object {
        // Handling Doctype value (DocumentType enum value)
        const val PARAM_DOCTYPE_KEY = "doctype"
        // Handling Online mode (boolean value)
        const val PARAM_ONLINE_KEY = "online"
        // Handling CISContext values for online Liveness
        const val PARAM_FOLDER_KEY = "folder"
        const val PARAM_DOCUMENT_KEY = "documentUid"
        const val PARAM_TASK_KEY = "taskUid"
        // Handling liveness token for offline mode (String value)
        const val PARAM_LIVENESS_TOKEN_KEY = "token"

        // Result data
        const val PARAM_RESULT_NAME_KEY = "name"
        const val PARAM_RESULT_FOLDER_KEY = "folderUid"
        const val PARAM_RESULT_DOCUMENT_KEY = "documentUid"
        const val PARAM_RESULT_TASK_KEY = "taskUid"
    }

    private var docType: DocumentType? = null
    private var isOnline: Boolean = false
    private var livenessToken: String? = null
    private lateinit var cisContext: CISContext

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intent.extras?.let { extras ->
            Log.d(CaptureActivity::class.java.simpleName, "Reading extra values from intent :")
            docType = extras.getString(PARAM_DOCTYPE_KEY)?.let { DocumentType.valueOf(it) }
            isOnline = extras.getBoolean(PARAM_ONLINE_KEY, false)
            livenessToken = extras.getString(PARAM_LIVENESS_TOKEN_KEY)
            cisContext = CISContext(
                extras.getString(PARAM_FOLDER_KEY),
                extras.getString(PARAM_TASK_KEY),
                extras.getString(PARAM_DOCUMENT_KEY)
            )
        } ?: run {
            cisContext = CISContext(null, null, null)
        }

        setContentView(R.layout.activity_capture)
    }

    override fun onResume() {
        super.onResume()

        setupCapture()

        startScanning()
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // INTERFACE : IdcheckioInteractionInterface
    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    // Handle here all SDK interactions ()
    override fun onIdcheckioInteraction(interaction: IdcheckioInteraction, data: Any?) {
        when (interaction) {
            // Here we get the resulting data and images from the SDK
            IdcheckioInteraction.RESULT -> {
                returnResult(data)
            }
            // Here we are notified in case of an error to inform the final user
            IdcheckioInteraction.ERROR -> {
                (data as? ErrorMsg)?.let { errorMsg ->
                    Toast.makeText(this, "SDK ERROR : $errorMsg", Toast.LENGTH_LONG)
                        .show()
                    finish()
                }
            }
            // We can filter the cameras
            IdcheckioInteraction.UPDATE_CAMERA_LIST,
                // We can display a custom view with the extracted data
            IdcheckioInteraction.DATA,
                // We can display a custom overlay view based on the quad of the cropped document found in the scene
            IdcheckioInteraction.QUAD,
                // We can override UI Messages for a custom display
            IdcheckioInteraction.UI -> {
                // Do nothing
            }
        }
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Private functions
    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private fun setupCapture() {
        docType?.let { docType ->
            // Pass the DocumentType to the SDK
            sdk_view.setDocumentType(docType)

            when (docType) {
                DocumentType.ID -> setupIDCapture()
                DocumentType.LIVENESS -> setupLivenessCapture()
                else -> {
                    // do nothing, let the SDK capture parameters by default
                }
            }
        }
        sdk_view.setInteractionListener(this)
    }

    private fun setupLivenessCapture() {
        // Portrait orientation is recommended for Liveness
        sdk_view.addParameters(EnumParameters.ORIENTATION, Orientation.PORTRAIT)

        // Handle liveness token for offline mode
        if (!isOnline) {
            livenessToken?.let {
                sdk_view.addExtraParameters(EnumExtraParameters.TOKEN, it)
            }
        }
    }

    private fun setupIDCapture() {
        // Display the cropped picture
        sdk_view.addParameters(EnumParameters.CONFIRM_TYPE, ConfirmationType.CROPPED_PICTURE)
        // Data extraction from the ID Document
        // First side must contain a valid codeline, plus the SDK will try to extract a face picture
        sdk_view.addParameters(
            EnumParameters.SIDE_1_EXTRACTION,
            Extraction(DataRequirement.DECODED, FaceDetection.ENABLED)
        )
        // Second side must not contain any codeline
        sdk_view.addParameters(
            EnumParameters.SIDE_2_EXTRACTION,
            Extraction(DataRequirement.REJECT, FaceDetection.DISABLED)
        )

        // Allow the user to skip scanning the second side of document
        sdk_view.addParameters(EnumParameters.SCAN_BOTH_SIDES, Forceable.ENABLED)

        // Display manual capture button after 5s if no document was found
        sdk_view.addExtraParameters(EnumExtraParameters.MANUAL_BUTTON_TIMER, 30)
    }

    private fun startScanning() {
        if (isOnline) {
            sdk_view.startOnline("licence", this, cisContext)
        } else {
            sdk_view.start()
        }
    }

    private fun returnResult(data: Any?) {
        val resultIntent = Intent()
        (data as? IdcheckioResult)?.let {
            (data.document as? Document.IdentityDocument)?.let { document ->
                var name = ""
                document.fields[Document.IdentityDocument.Field.gender]?.let {
                    name += "${it.value} "
                }
                document.fields[Document.IdentityDocument.Field.lastNames]?.let {
                    name += "${it.value} "
                }
                document.fields[Document.IdentityDocument.Field.firstNames]?.let {
                    name += "${it.value} "
                }
                // Return the extracted gender/firstname/name data
                resultIntent.putExtra(PARAM_RESULT_NAME_KEY, name)
            }

            // Return CIS data if available
            data.folderUid?.let {
                resultIntent.putExtra(PARAM_RESULT_FOLDER_KEY, it)
            }
            data.documentUid?.let {
                resultIntent.putExtra(PARAM_RESULT_DOCUMENT_KEY, it)
            }
            data.taskUid?.let {
                resultIntent.putExtra(PARAM_RESULT_TASK_KEY, it)
            }

            setResult(Activity.RESULT_OK, resultIntent)
        } ?: run {
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }
}
