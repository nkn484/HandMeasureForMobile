package com.resources.handmeasure.sdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.resources.handmeasure.sdk.api.HandMeasureOutcome
import com.resources.handmeasure.sdk.api.HandMeasureRequest

class HandMeasureContract : ActivityResultContract<HandMeasureRequest, HandMeasureOutcome>() {
    override fun createIntent(context: Context, input: HandMeasureRequest): Intent {
        return Intent(context, HandMeasureActivity::class.java).apply {
            putExtra(EXTRA_REQUEST, input)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): HandMeasureOutcome {
        val outcome = intent?.getParcelableExtra<HandMeasureOutcome>(EXTRA_OUTCOME)
        if (resultCode == Activity.RESULT_OK && outcome != null) return outcome

        // If the Activity didn't return a structured outcome (crash/process death/back press without setResult),
        // do NOT mislabel it as a user-cancel.
        return outcome ?: HandMeasureOutcome.Cancelled(com.resources.handmeasure.sdk.api.CancelReason.UNKNOWN)
    }

    companion object {
        const val EXTRA_REQUEST = "hand_measure_request"
        const val EXTRA_OUTCOME = "hand_measure_outcome"
    }
}
