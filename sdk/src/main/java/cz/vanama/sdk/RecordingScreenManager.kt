package cz.vanama.sdk

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.shape.MaterialShapeDrawable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.sqrt
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

/**
 * The `RecordingScreenManager` class provides functionality for creating a wireframe
 * rendering of an activity's screen.
 *
 * The wireframe is a representation of the activity's view tree, including all visible elements.
 * The resulting wireframe can be returned as a `Bitmap` or stored to cache and displayed in a new activity.
 *
 * @author Martin Vana
 */
class RecordingScreenManager {

    private lateinit var canvas: Canvas

    /**
     * Creates a wireframe rendering of an activity's screen and returns it as a `Bitmap`.
     *
     * This method traverses the activity's view tree and processes each view. The time taken to process all views
     * is logged for performance monitoring purposes.
     *
     * @param activity the Activity whose screen should be captured.
     * @return a `Bitmap` containing the rendered wireframe.
     */
    @OptIn(ExperimentalTime::class)
    suspend fun renderWireframe(activity: Activity): Bitmap = withContext(Dispatchers.IO) {
        val rootView = activity.window.decorView.rootView
        val bitmap = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
        canvas = Canvas(bitmap)
        val time = measureTime {
            processViews(rootView)
        }
        Log.d(TAG, "process views: $time")
        bitmap
    }

    /**
     * Creates a wireframe rendering of an activity's screen, stores it to the activity's cache,
     * and displays the wireframe in a new activity.
     *
     * This method traverses the activity's view tree and processes each view. The time taken to process all views
     * is logged for performance monitoring purposes.
     * The wireframe is then stored to the cache directory provided by the activity.
     * Finally, a new activity is started to display the stored wireframe.
     *
     * @param activity the Activity whose screen should be captured.
     */
    @OptIn(ExperimentalTime::class)
    fun renderWireframeAndShow(activity: Activity) {
        CoroutineScope(Dispatchers.IO).launch {
            val rootView = activity.window.decorView.rootView
            val bitmap = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
            canvas = Canvas(bitmap)

            val time = measureTime {
                processViews(rootView)
            }

            Log.d(TAG, "process views: $time")

            // Store bitmap also needs to be offloaded to the IO dispatcher
            storeBitmap(bitmap, activity.cacheDir)

            // Switch to the Main thread to launch the new activity
            withContext(Dispatchers.Main) {
                val intent = Intent(activity, NewActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                intent.putExtra("image_count", 1)
                activity.startActivity(intent)
            }
        }
    }

    private fun storeBitmap(bitmap: Bitmap, cacheDir: File) {
        val file = File(cacheDir, "my_image0.png")
        var fileOutputStream: FileOutputStream? = null
        try {
            fileOutputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, fileOutputStream)
        } catch (exception: IOException) {
            // Handle the exception properly. For instance, log the error or inform the user.
            Log.e(TAG, "Error storing bitmap", exception)
        } finally {
            try {
                fileOutputStream?.close()
            } catch (exception: IOException) {
                // Ignore or log the closing failure
                Log.e(TAG, "Error closing FileOutputStream", exception)
            }
        }
        // Ensure that the bitmap is not in use before recycling.
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    private fun processViews(view: View) {
        if (view.isShown && isInScreenBounds(view)) {
            processBackgroundDrawable(view)
            when (view) {
                is TextView -> processTextView(view)
                is ImageView -> processImageView(view)
                is ViewGroup -> {
                    for (i in 0 until view.childCount) {
                        val child = view.getChildAt(i)
                        processViews(child)
                    }
                }
            }
        }
    }

    private fun isInScreenBounds(view: View): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val x = location[0]
        val y = location[1]

        return x >= 0 && y >= 0 && x <= canvas.width && y <= canvas.height
    }

    private fun processTextView(view: TextView) = with(view) {
        val rectPaint = Paint()
        rectPaint.color = textColors.defaultColor
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val left = location[0]
        val top = location[1]

        if (width > 0 && height > 0) {
            val drawablePadding = compoundDrawablePadding
            val leftDrawable = compoundDrawables[0]
            processTextViewDrawables(this, left, top)

            for (i in 0 until layout.lineCount) {
                val lineStart = layout.getLineStart(i)
                val lineEnd = layout.getLineEnd(i)
                val lineText = text.substring(lineStart, lineEnd)

                if (view is CheckedTextView && view.checkMarkDrawable != null) {
                    processDrawable(view.checkMarkDrawable, left, top)
                }

                val lineWidth = paint.measureText(lineText)

                canvas.drawRoundRect(
                    left + paddingLeft.toFloat() + (leftDrawable?.bounds?.width()
                        ?.let { it + drawablePadding } ?: 0),
                    top + (lineHeight.toFloat() * i) + paddingTop + drawablePadding,
                    left + lineWidth + paddingLeft.toFloat() + (leftDrawable?.bounds?.width()
                        ?.let { it + drawablePadding } ?: 0),
                    top + (lineHeight.toFloat() * (i + 1)) + paddingTop + drawablePadding,
                    lineHeight.toFloat(),
                    lineHeight.toFloat(),
                    rectPaint
                )
            }
        }
    }

    private fun processTextViewDrawables(textView: TextView, left: Int, top: Int) = with(textView) {
        compoundDrawablesRelative.forEachIndexed { index, drawable ->
            drawable?.let {
                val drawablePadding = compoundDrawablePadding

                val drawableLeft = when (index) {
                    0 -> left + paddingLeft
                    1, 3 -> left + (width - paddingRight - drawable.bounds.width() - drawablePadding)
                    else -> left + paddingLeft + drawable.bounds.width() + drawablePadding
                }

                val drawableTop = when (index) {
                    0, 2 -> top + paddingTop + drawablePadding
                    1 -> top + paddingTop
                    else -> top + (height - paddingBottom - drawable.bounds.height() - drawablePadding)
                }

                processDrawable(it, drawableLeft, drawableTop)
            }
        }
    }

    private fun processImageView(view: ImageView) = with(view) {
        if (drawable != null && width > 0 && height > 0) {
            val location = IntArray(2)
            view.getLocationInWindow(location)
            val left = location[0]
            val top = location[1]

            if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                val paint = Paint()
                when (drawable) {
                    is ColorDrawable -> paint.color = (drawable as ColorDrawable).color
                    else -> {
                        val bitmap = drawableToBitmap(drawable)
                        val color = getDominantColor(bitmap)
                        paint.color = color
                    }
                }
                canvas.drawRect(
                    left.toFloat() + paddingLeft + drawable.bounds.left,
                    top.toFloat() + paddingTop + drawable.bounds.top,
                    left.toFloat() + paddingLeft + width,
                    top.toFloat() + paddingTop + height,
                    paint
                )
            }
        }
    }

    private fun processDrawable(drawable: Drawable, left: Int, top: Int) {
        if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
            val paint = Paint()
            when (drawable) {
                is ColorDrawable -> paint.color = drawable.color
                else -> {
                    paint.color = getDominantColor(drawableToBitmap(drawable))
                }
            }
            canvas.drawRect(
                left.toFloat() + drawable.bounds.left,
                top.toFloat() + drawable.bounds.top,
                left.toFloat() + drawable.bounds.left + drawable.intrinsicWidth.toFloat(),
                top.toFloat() + drawable.bounds.top + drawable.intrinsicHeight.toFloat(),
                paint
            )
        }
    }

    private fun processBackgroundDrawable(view: View) = with(view) {
        if (background != null && width > 0 && height > 0) {
            val drawable = background

            val location = IntArray(2)
            view.getLocationInWindow(location)
            val left = location[0]
            val top = location[1]

            val paint = Paint()
            paint.color = when (drawable) {
                is ColorDrawable -> drawable.color
                else -> {
                    if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                        getDominantColor(drawableToBitmap(drawable))
                    } else {
                        Color.TRANSPARENT
                    }
                }
            }
            if (drawable is MaterialShapeDrawable) {
                val constantState = drawable.constantState
                if (constantState != null) {
                    val copyDrawable = constantState.newDrawable().mutate()
                    copyDrawable.setBounds(left, top, left + width, top + height)
                    copyDrawable.draw(canvas)
                }
            } else {
                canvas.drawRect(
                    left.toFloat(),
                    top.toFloat(),
                    (left + width).toFloat(),
                    (top + height).toFloat(),
                    paint
                )
            }
        }
    }

    private fun getDominantColor(bitmap: Bitmap): Int {
        if (bitmap.width > 0 && bitmap.height > 0) {
            val width: Int
            val height: Int
            val aspectRatio = bitmap.width.toDouble() / bitmap.height.toDouble()

            if (bitmap.width * bitmap.height > 10000) {
                if (bitmap.width > bitmap.height) {
                    width = (100 * sqrt(aspectRatio)).toInt()
                    height = (100 / sqrt(aspectRatio)).toInt()
                } else {
                    height = (100 * sqrt(aspectRatio)).toInt()
                    width = (100 / sqrt(aspectRatio)).toInt()
                }
            } else {
                width = bitmap.width
                height = bitmap.height
            }

            val sampledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false)

            val size = sampledBitmap.width * sampledBitmap.height
            val pixels = IntArray(size)

            sampledBitmap.getPixels(
                pixels,
                0,
                sampledBitmap.width,
                0,
                0,
                sampledBitmap.width,
                sampledBitmap.height
            )

            val colorMap = mutableMapOf<Int, Int>()

            for (pixel in pixels) {
                val alpha = Color.alpha(pixel)
                if (alpha < 128) // Skip transparent pixels
                    continue

                // Adjust color for semi-transparent pixels
                val red = (Color.red(pixel) * (alpha / 255.0)).toInt()
                val green = (Color.green(pixel) * (alpha / 255.0)).toInt()
                val blue = (Color.blue(pixel) * (alpha / 255.0)).toInt()

                val adjustedPixel = Color.argb(255, red, green, blue)

                if (colorMap.containsKey(adjustedPixel))
                    colorMap[adjustedPixel] = colorMap[adjustedPixel]!! + 1
                else
                    colorMap[adjustedPixel] = 1
            }

            return (colorMap.maxByOrNull { it.value }?.key ?: Color.TRANSPARENT).also {
                Log.d(TAG, "Dominant color: ${Integer.toHexString(it)}")
            }
        } else return Color.TRANSPARENT
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    companion object {
        private const val TAG = "RecordingScreenManager"
    }
}