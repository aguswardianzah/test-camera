package com.example.testcamera

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.testcamera.databinding.FragmentResultBinding
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage

class ResultActivity : AppCompatActivity() {

    private val viewBinding by lazy { FragmentResultBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(viewBinding.root)

        val storage = Firebase.storage

        // Create a storage reference from our app
        val storageRef = storage.reference.child("result.txt")

        storageRef.getBytes(Long.MAX_VALUE).addOnSuccessListener {
            viewBinding.result.setText(String(it))
        }.addOnFailureListener {
            Log.e("", "failed to read result file", it)
        }
    }

}