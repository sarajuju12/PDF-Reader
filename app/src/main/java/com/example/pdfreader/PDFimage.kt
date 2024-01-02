package com.example.pdfreader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.Color.BLUE
import android.graphics.Color.TRANSPARENT
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.ImageView
import android.view.ScaleGestureDetector
import android.widget.RadioButton
import android.widget.RadioGroup
import java.util.ArrayList

@SuppressLint("AppCompatCustomView")
class PDFimage  // constructor
    (context: Context?, private val radioGroup: RadioGroup) : ImageView(context) {
    val LOGNAME = "panzoom"

    // drawing
    var path: Path? = null
    var paths: ArrayList<Pair<Path?, Paint>> = ArrayList()
    var paintbrush = Paint(BLUE)
    var background: Bitmap? = null
    var pdfPage: Bitmap? = null
    var bluePaint = Paint(BLUE) // Separate Paint object for radioButton1 strokes
    var yellowPaint = Paint(Color.YELLOW)

    // we save a lot of points because they need to be processed
    // during touch events e.g. ACTION_MOVE
    var x1 = 0f
    var x2 = 0f
    var y1 = 0f
    var y2 = 0f
    var old_x1 = 0f
    var old_y1 = 0f
    var old_x2 = 0f
    var old_y2 = 0f
    var mid_x = -1f
    var mid_y = -1f
    var old_mid_x = -1f
    var old_mid_y = -1f
    var p1_id = 0
    var p1_index = 0
    var p2_id = 0
    var p2_index = 0

    //private var isPanning = false
    private var initialX1 = 0f
    private var initialY1 = 0f
    var savedMatrix: Matrix? = null

    // store cumulative transformations
    // the inverse matrix is used to align points with the transformations - see below
    var currentMatrix = Matrix()
    var inverse = Matrix()

    private val pathsMap: MutableMap<Int, MutableList<Pair<Path, Paint>>> = mutableMapOf()
    private var currentPage: Int = 1

    private val undoMap: MutableMap<Int, MutableList<Any>> = mutableMapOf()
    private val undoIndexMap: MutableList<Int> = MutableList(size = 55) { -1 }
    private val redoMap: MutableMap<Int, MutableList<Any>> = mutableMapOf()
    private val redoIndexMap: MutableList<Int> = MutableList(size = 55) { -1 }

    private fun isTouchCloseToPath(path: Path, x: Float, y: Float, touchTolerance: Float): Boolean {
        val measure = PathMeasure(path, false)
        val coords = floatArrayOf(0f, 0f)

        val length = measure.length
        val increment = 2f // Adjust the increment as needed for more accurate detection
        var distance = 0f

        while (distance < length) {
            measure.getPosTan(distance, coords, null)
            val dx = coords[0] - x
            val dy = coords[1] - y
            if (dx * dx + dy * dy < touchTolerance * touchTolerance) {
                return true
            }
            distance += increment
        }
        return false
    }

    // capture touch events (down/move/up) to create a path/stroke that we draw later
    override fun onTouchEvent(event: MotionEvent): Boolean {
        var inverted: FloatArray
        when (event.pointerCount) {
            1 -> {
                p1_id = event.getPointerId(0)
                p1_index = event.findPointerIndex(p1_id)

                // invert using the current matrix to account for pan/scale
                // inverts in-place and returns boolean
                inverse = Matrix()
                currentMatrix.invert(inverse)

                // mapPoints returns values in-place
                inverted = floatArrayOf(event.getX(p1_index), event.getY(p1_index))
                inverse.mapPoints(inverted)
                x1 = inverted[0]
                y1 = inverted[1]
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        try {
                            if (radioGroup.checkedRadioButtonId == R.id.radioButton1) {
                                bluePaint.style = Paint.Style.STROKE
                                bluePaint.strokeWidth = 5f
                                bluePaint.color = BLUE
                                redoIndexMap[currentPage] = -1
                                redoMap[currentPage]?.clear()
                                val currentPagePaths = pathsMap.getOrPut(currentPage) { mutableListOf() }
                                path = Path()
                                val myPair: Pair<Path?, Paint> = Pair(path, bluePaint)
                                currentPagePaths.add(myPair as Pair<Path, Paint>)
                                paths.add(myPair)
                                val index = currentPagePaths.indexOf(myPair)
                                val currentUndoPaths = undoMap.getOrPut(currentPage) { mutableListOf() }
                                currentUndoPaths.add(index)
                                if (undoIndexMap[currentPage] == -1) {
                                    var undoIndex = 0
                                    undoIndexMap[currentPage] = undoIndex
                                } else {
                                    var undoIndex = undoIndexMap[currentPage]
                                    undoIndex++
                                    undoIndexMap[currentPage] = undoIndex
                                }
                                path!!.moveTo(x1, y1)
                                invalidate()
                            } else if (radioGroup.checkedRadioButtonId == R.id.radioButton2) {
                                yellowPaint.style = Paint.Style.STROKE
                                yellowPaint.color = Color.YELLOW
                                yellowPaint.strokeWidth = 20f
                                yellowPaint.alpha = 100
                                redoIndexMap[currentPage] = -1
                                redoMap[currentPage]?.clear()
                                val currentPagePaths = pathsMap.getOrPut(currentPage) { mutableListOf() }
                                path = Path()
                                val myPair: Pair<Path?, Paint> = Pair(path, yellowPaint)
                                currentPagePaths.add(myPair as Pair<Path, Paint>)
                                paths.add(myPair)
                                val index = currentPagePaths.indexOf(myPair)
                                val currentUndoPaths = undoMap.getOrPut(currentPage) { mutableListOf() }
                                currentUndoPaths.add(index)
                                if (undoIndexMap[currentPage] == -1) {
                                    var undoIndex = 0
                                    undoIndexMap[currentPage] = undoIndex
                                } else {
                                    var undoIndex = undoIndexMap[currentPage]
                                    undoIndex++
                                    undoIndexMap[currentPage] = undoIndex
                                }
                                path!!.moveTo(x1, y1)
                                invalidate()
                            } else if (radioGroup.checkedRadioButtonId == R.id.radioButton4) {
                                // Save the initial touch position for panning
                                initialX1 = x1
                                initialY1 = y1
                                // Save the current matrix to calculate translation later
                                savedMatrix = Matrix(currentMatrix)
                                currentMatrix.set(savedMatrix)
                            } else if (radioGroup.checkedRadioButtonId == R.id.radioButton3) {
                                redoIndexMap[currentPage] = -1
                                redoMap[currentPage]?.clear()
                                var intersectionFound = false
                                val currentPagePaths = pathsMap[currentPage]
                                val iterator = currentPagePaths?.iterator()
                                var currentIndex = -1
                                if (iterator != null) {
                                    while (iterator.hasNext()) {
                                        currentIndex++
                                        val (currentPath, _) = iterator.next()
                                        currentPath?.let {
                                            if (isTouchCloseToPath(it, x1, y1, 20f)) {
                                                val currentUndoPaths = undoMap.getOrPut(currentPage) { mutableListOf() }
                                                val toAdd: Pair<Int, Pair<Path, Paint>> = Pair(currentIndex, currentPagePaths[currentIndex])
                                                currentUndoPaths.add(toAdd)
                                                if (undoIndexMap[currentPage] == -1) {
                                                    var undoIndex = 0
                                                    undoIndexMap[currentPage] = undoIndex
                                                } else {
                                                    var undoIndex = undoIndexMap[currentPage]
                                                    undoIndex++
                                                    undoIndexMap[currentPage] = undoIndex
                                                }
                                                currentPagePaths[currentIndex] = Pair(Path(), Paint())
                                                intersectionFound = true
                                            }
                                        }
                                        if (intersectionFound) {
                                            invalidate()
                                            break
                                        }
                                    }
                                }
                            }
                        } catch (e: NullPointerException) {

                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        try {
                            if (radioGroup.checkedRadioButtonId == R.id.radioButton1) {
                                bluePaint.style = Paint.Style.STROKE
                                bluePaint.strokeWidth = 5f
                                bluePaint.color = BLUE
                                path!!.lineTo(x1, y1)
                            } else if (radioGroup.checkedRadioButtonId == R.id.radioButton4) {
                                val dx = x1 - initialX1
                                val dy = y1 - initialY1
                                currentMatrix.set(savedMatrix)
                                currentMatrix.postTranslate(dx, dy)
                                invalidate()
                            } else if (radioGroup.checkedRadioButtonId == R.id.radioButton2) {
                                yellowPaint.style = Paint.Style.STROKE
                                yellowPaint.color = Color.YELLOW
                                yellowPaint.strokeWidth = 20f
                                yellowPaint.alpha = 100
                                path!!.lineTo(x1, y1)
                            }
                        } catch (e: NullPointerException) {

                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        Log.d(LOGNAME, "Action up")
                    }
                }
            }
            2 -> {
                // point 1
                p1_id = event.getPointerId(0)
                p1_index = event.findPointerIndex(p1_id)

                // mapPoints returns values in-place
                inverted = floatArrayOf(event.getX(p1_index), event.getY(p1_index))
                inverse.mapPoints(inverted)

                // first pass, initialize the old == current value
                if (old_x1 < 0 || old_y1 < 0) {
                    x1 = inverted.get(0)
                    old_x1 = x1
                    y1 = inverted.get(1)
                    old_y1 = y1
                } else {
                    old_x1 = x1
                    old_y1 = y1
                    x1 = inverted.get(0)
                    y1 = inverted.get(1)
                }

                // point 2
                p2_id = event.getPointerId(1)
                p2_index = event.findPointerIndex(p2_id)

                // mapPoints returns values in-place
                inverted = floatArrayOf(event.getX(p2_index), event.getY(p2_index))
                inverse.mapPoints(inverted)

                // first pass, initialize the old == current value
                if (old_x2 < 0 || old_y2 < 0) {
                    x2 = inverted.get(0)
                    old_x2 = x2
                    y2 = inverted.get(1)
                    old_y2 = y2
                } else {
                    old_x2 = x2
                    old_y2 = y2
                    x2 = inverted.get(0)
                    y2 = inverted.get(1)
                }

                // midpoint
                mid_x = (x1 + x2) / 2
                mid_y = (y1 + y2) / 2
                old_mid_x = (old_x1 + old_x2) / 2
                old_mid_y = (old_y1 + old_y2) / 2

                // distance
                val d_old =
                    Math.sqrt(Math.pow((old_x1 - old_x2).toDouble(), 2.0) + Math.pow((old_y1 - old_y2).toDouble(), 2.0))
                        .toFloat()
                val d = Math.sqrt(Math.pow((x1 - x2).toDouble(), 2.0) + Math.pow((y1 - y2).toDouble(), 2.0))
                    .toFloat()

                // pan and zoom during MOVE event
                if (event.action == MotionEvent.ACTION_MOVE) {
                    Log.d(LOGNAME, "Multitouch move")
                    // pan == translate of midpoint
                    val dx = mid_x - old_mid_x
                    val dy = mid_y - old_mid_y
                    currentMatrix.preTranslate(dx, dy)
                    Log.d(LOGNAME, "translate: $dx,$dy")

                    // zoom == change of spread between p1 and p2
                    var scale = d / d_old
                    scale = Math.max(0f, scale)
                    currentMatrix.preScale(scale, scale, mid_x, mid_y)
                    Log.d(LOGNAME, "scale: $scale")

                    // reset on up
                } else if (event.action == MotionEvent.ACTION_UP) {
                    old_x1 = -1f
                    old_y1 = -1f
                    old_x2 = -1f
                    old_y2 = -1f
                    old_mid_x = -1f
                    old_mid_y = -1f
                }
            }
            else -> {
            }
        }
        return true
    }

    // set image as background
    fun setImage(bitmap: Bitmap?) {
        background = bitmap
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scaleFactor = width.toFloat() / (background?.width ?: 1)
        val scalingMatrix = Matrix()
        scalingMatrix.setScale(scaleFactor, scaleFactor)
        canvas.concat(currentMatrix)

        if (background != null) {
            pdfPage = background
            val scaledBackground = Bitmap.createBitmap(
                pdfPage!!, 0, 0, pdfPage!!.width, pdfPage!!.height, scalingMatrix, true
            )
            canvas.drawBitmap(scaledBackground, 0f, 0f, null)
            val blankPageWidth = 1080
            val blankPageHeight = 1920
            val blankPageBitmap = createBlankWhitePage(blankPageWidth, blankPageHeight)
            setImageBitmap(blankPageBitmap)
        }

        val currentPagePaths = pathsMap[currentPage]
        if (currentPagePaths != null) {
            for (path in currentPagePaths) {
                if (path != null) {
                    path!!.first?.let { canvas.drawPath(it, path!!.second) }
                }
            }
        }
    }

    private fun createBlankWhitePage(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        return bitmap
    }

    fun changePage(pageNumber: Int) {
        currentPage = pageNumber
        invalidate()
    }

    fun undo() {
        val currentUndoPaths = undoMap.getOrPut(currentPage) { mutableListOf() }
        val currentRedoPaths = redoMap.getOrPut(currentPage) { mutableListOf() }
        var undoIndex = undoIndexMap[currentPage]
        if (undoIndex >= 0) {
            val undoThing = currentUndoPaths[undoIndex]
            val currentPagePaths = pathsMap[currentPage]
            if (undoThing is Int) {
                val toAdd: Pair<Int, Pair<Path, Paint>?> = Pair(undoThing, currentPagePaths?.get(undoThing))
                currentPagePaths?.removeAt(undoThing)
                currentRedoPaths.add(toAdd)
                if (redoIndexMap[currentPage] == -1) {
                    var redoIndex = 0
                    redoIndexMap[currentPage] = redoIndex
                } else {
                    var redoIndex = redoIndexMap[currentPage]
                    redoIndex++
                    redoIndexMap[currentPage] = redoIndex
                }
            } else if (undoThing is Pair<*, *>){
                undoThing as Pair<Int, Pair<Path, Paint>>
                currentPagePaths?.set(undoThing.first, undoThing.second)
                currentRedoPaths.add(undoThing.first)
                if (redoIndexMap[currentPage] == -1) {
                    var redoIndex = 0
                    redoIndexMap[currentPage] = redoIndex
                } else {
                    var redoIndex = redoIndexMap[currentPage]
                    redoIndex++
                    redoIndexMap[currentPage] = redoIndex
                }
            }
            currentUndoPaths.removeAt(undoIndex)
            undoIndex--
            undoIndexMap[currentPage] = undoIndex
            invalidate()
        }
    }

    fun redo() {
        val currentUndoPaths = undoMap.getOrPut(currentPage) { mutableListOf() }
        val currentRedoPaths = redoMap.getOrPut(currentPage) { mutableListOf() }
        var redoIndex = redoIndexMap[currentPage]
        if (redoIndex >= 0) {
            val redoThing = currentRedoPaths[redoIndex]
            val currentPagePaths = pathsMap[currentPage]
            if (redoThing is Int) {
                val toAdd: Pair<Int, Pair<Path, Paint>?> = Pair(redoThing, currentPagePaths?.get(redoThing))
                currentUndoPaths.add(toAdd)
                if (undoIndexMap[currentPage] == -1) {
                    var undoIndex = 0
                    undoIndexMap[currentPage] = undoIndex
                } else {
                    var undoIndex = undoIndexMap[currentPage]
                    undoIndex++
                    undoIndexMap[currentPage] = undoIndex
                }
                currentPagePaths?.set(redoThing, Pair(Path(), Paint()))
            } else if (redoThing is Pair<*, *>){
                redoThing as Pair<Int, Pair<Path, Paint>>
                val myPair: Pair<Path?, Paint> = redoThing.second
                currentPagePaths?.add(myPair as Pair<Path, Paint>)
                val index = currentPagePaths?.indexOf(myPair)
                if (index != null) {
                    currentUndoPaths.add(index)
                }
                if (undoIndexMap[currentPage] == -1) {
                    var undoIndex = 0
                    undoIndexMap[currentPage] = undoIndex
                } else {
                    var undoIndex = undoIndexMap[currentPage]
                    undoIndex++
                    undoIndexMap[currentPage] = undoIndex
                }

            }
            currentRedoPaths.removeAt(redoIndex)
            redoIndex--
            redoIndexMap[currentPage] = redoIndex
            invalidate()
        }
    }

    // constructor
    init {
        paintbrush.style = Paint.Style.STROKE
        paintbrush.strokeWidth = 5f
    }
}

