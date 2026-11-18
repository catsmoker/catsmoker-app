package com.catsmoker.app.screens.gaming.automation

import org.json.JSONObject

internal object AutomationStatus {
    fun afterProcessStart(saved: String?): String = runCatching {
        val value = JSONObject(saved ?: response("idle"))
        require(value.getInt("version") == 1)
        if (value.getString("result") == "running") response("interrupted")
        else value.toString()
    }.getOrElse { response("unknown") }

    fun response(code: String): String = JSONObject().put("version", 1).put("result", code)
        .put("time", System.currentTimeMillis()).toString()
}
