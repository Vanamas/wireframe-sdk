package cz.vanama.sdk

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2

class NewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new)

        findViewById<ViewPager2>(R.id.viewPager).apply {
            adapter = ImagePagerAdapter(intent.getIntExtra("image_count", 0), cacheDir)
        }
    }
}