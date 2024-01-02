package com.example.pdfreader

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import java.io.*

// PDF sample code from
// https://medium.com/@chahat.jain0/rendering-a-pdf-document-in-android-activity-fragment-using-pdfrenderer-442462cb8f9a
// Issues about cache etc. are not at all obvious from documentation, so we should expect people to need this.
// We may wish to provide this code.
class MainActivity : AppCompatActivity() {
    val LOGNAME = "pdf_viewer"
    val FILENAME = "shannon1948.pdf"
    val FILERESID = R.raw.shannon1948

    // manage the pages of the PDF, see below
    lateinit var pdfRenderer: PdfRenderer
    lateinit var parcelFileDescriptor: ParcelFileDescriptor
    var currentPage: PdfRenderer.Page? = null
    var currentPageIndex = 0
    var totalPages = 0

    // custom ImageView class that captures strokes and draws them over the image
    lateinit var pageImage: PDFimage
    lateinit var toolbar: Toolbar
    lateinit var statusBar: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        toolbar = findViewById(R.id.toolbar)
        setActionBar(toolbar)

        val undoButton = ImageButton(this)
        undoButton.setImageResource(R.drawable.ic_undo)
        undoButton.setOnClickListener {
            pageImage.undo()
        }
        toolbar.addView(undoButton)

        val redoButton = ImageButton(this)
        redoButton.setImageResource(R.drawable.ic_redo)
        redoButton.setOnClickListener {
            pageImage.redo()
        }
        toolbar.addView(redoButton)

        val radioGroupLayout = layoutInflater.inflate(R.layout.radio_group, null)
        val radioGroup = radioGroupLayout.findViewById<RadioGroup>(R.id.radioGroup)
        toolbar.addView(radioGroup)

        val params = Toolbar.LayoutParams(
            Toolbar.LayoutParams.WRAP_CONTENT,
            Toolbar.LayoutParams.WRAP_CONTENT
        )
        params.gravity = Gravity.END

        val buttonPrevious = ImageButton(this)
        buttonPrevious.setImageResource(R.drawable.ic_previous)

        val buttonNext = ImageButton(this)
        buttonNext.setImageResource(R.drawable.ic_next)

        buttonNext.layoutParams = params
        buttonPrevious.layoutParams = params
        toolbar.addView(buttonNext)
        toolbar.addView(buttonPrevious)

        actionBar?.title = FILENAME

        val layout = findViewById<LinearLayout>(R.id.pdfLayout)
        layout.isEnabled = true

        pageImage = PDFimage(this, radioGroup)
        layout.addView(pageImage)
        pageImage.minimumWidth = 1000
        pageImage.minimumHeight = 2000

        // open page 0 of the PDF
        // it will be displayed as an image in the pageImage (above)
        try {
            openRenderer(this)
            showPage(currentPageIndex)
            totalPages = pdfRenderer.pageCount
            statusBar = findViewById(R.id.statusLabel)
            statusBar.text = "Page ${currentPageIndex+1}/$totalPages"
        } catch (exception: IOException) {
            Log.d(LOGNAME, "Error opening PDF")
        }
        buttonPrevious.setOnClickListener {
            changePages(false)
        }
        buttonNext.setOnClickListener {
            changePages(true)
        }
    }

    override fun onPause() {
        super.onPause()
        saveChangesToFile(this)
    }

    override fun onResume() {
        super.onResume()
        loadChangesFromFile(this)
        openRenderer(this)
    }

    override fun onStop() {
        super.onStop()
        try {
            if (currentPage != null) {
                currentPage!!.close()
                currentPage = null
            }
            if (::pdfRenderer.isInitialized) {
                pdfRenderer.close()
                parcelFileDescriptor.close()
            }
        } catch (ex: IOException) {
            Log.d(LOGNAME, "Unable to close PDF renderer")
        }
    }

    fun saveChangesToFile(context: Context) {
        try {
            val file = File(context.filesDir, "saved_data")
            val fileOutputStream = FileOutputStream(file)
            val objectOutputStream = ObjectOutputStream(fileOutputStream)
            objectOutputStream.writeInt(currentPageIndex)
            objectOutputStream.writeInt(totalPages)
            objectOutputStream.close()
            fileOutputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadChangesFromFile(context: Context) {
        try {
            val file = File(context.filesDir, "saved_data")
            if (file.exists()) {
                val fileInputStream = FileInputStream(file)
                val objectInputStream = ObjectInputStream(fileInputStream)
                val loadedCurrentPageIndex = objectInputStream.readInt()
                val loadedTotalPages = objectInputStream.readInt()
                currentPageIndex = loadedCurrentPageIndex
                totalPages = loadedTotalPages
                statusBar.text = "Page ${currentPageIndex + 1}/$totalPages"
                pageImage.changePage(currentPageIndex + 1)
                showPage(currentPageIndex)
                objectInputStream.close()
                fileInputStream.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun changePages(next: Boolean) {
        if (next && currentPageIndex < totalPages - 1) {
            currentPageIndex++
            statusBar = findViewById(R.id.statusLabel)
            statusBar.text = "Page ${currentPageIndex+1}/$totalPages"
            pageImage.changePage(currentPageIndex+1)
            showPage(currentPageIndex)
        } else if (!next && currentPageIndex > 0) {
            currentPageIndex--
            statusBar = findViewById(R.id.statusLabel)
            statusBar.text = "Page ${currentPageIndex+1}/$totalPages"
            pageImage.changePage(currentPageIndex+1)
            showPage(currentPageIndex)
        }
    }

    @Throws(IOException::class)
    private fun openRenderer(context: Context) {
        // In this sample, we read a PDF from the assets directory.
        val file = File(context.cacheDir, FILENAME)
        if (!file.exists()) {
            // pdfRenderer cannot handle the resource directly,
            // so extract it into the local cache directory.
            val asset = this.resources.openRawResource(FILERESID)
            val output = FileOutputStream(file)
            val buffer = ByteArray(1024)
            var size: Int
            while (asset.read(buffer).also { size = it } != -1) {
                output.write(buffer, 0, size)
            }
            asset.close()
            output.close()
        }
        parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

        // capture PDF data
        // all this just to get a handle to the actual PDF representation
        pdfRenderer = PdfRenderer(parcelFileDescriptor)
    }

    // do this before you quit!
    @Throws(IOException::class)
    private fun closeRenderer() {
        currentPage?.close()
        pdfRenderer.close()
        parcelFileDescriptor.close()
    }

    private fun showPage(index: Int) {
        if (pdfRenderer.pageCount <= index) {
            return
        }
        // Close the current page before opening another one.
        currentPage?.close()

        // Use `openPage` to open a specific page in PDF.
        currentPage = pdfRenderer.openPage(index)
        //pageImage.resetScaledBitmap()

        if (currentPage != null) {
            // Important: the destination bitmap must be ARGB (not RGB).
            val bitmap = Bitmap.createBitmap(currentPage!!.getWidth(), currentPage!!.getHeight(), Bitmap.Config.ARGB_8888)

            // Here, we render the page onto the Bitmap.
            // To render a portion of the page, use the second and third parameter. Pass nulls to get the default result.
            // Pass either RENDER_MODE_FOR_DISPLAY or RENDER_MODE_FOR_PRINT for the last parameter.
            currentPage!!.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            // Display the page
            pageImage.setImage(bitmap)
        }
    }
}