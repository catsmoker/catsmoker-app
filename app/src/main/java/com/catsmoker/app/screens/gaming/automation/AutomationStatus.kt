package com.catsmoker.app.screens.gaming.automation

import org.json.JSONObject

internal object AutomationStatus {
    fun afterProcessStart(saved: String?): String = runCatching {
        val value = JSONObject(saved ?: response("idle"))
        require(value.getInt("version") == 1)
        if (value.getString("result") in setOf("running", "accepted")) {
            value.put("result", "interrupted").put("time", System.currentTimeMillis())
        }
        value.toString()
    }.getOrElse { response("unknown") }

    fun matchingRequest(requestId: String, status: String, delivery: String): JSONObject? {
        val run = JSONObject(status).takeIf { it.optString("request_id") == requestId }
        val receipt = JSONObject(delivery).takeIf { it.optString("request_id") == requestId }
        if (run == null || receipt == null) return run ?: receipt
        // Prefer the detailed execution report, unless there is a distinct, later outcome such
        // as a game launcher refusal after preparation completed.
        return if (receipt.optLong("time") >= run.optLong("time") &&
            receipt.optString("result") !in setOf("accepted", run.optString("result"))) receipt else run
    }

    fun withRequest(result: String, request: AutomationProtocol.Request): String =
        JSONObject(result).apply {
            put("operation", request.operation.name)
            put("game", request.game ?: JSONObject.NULL)
            request.requestId?.let { put("request_id", it) }
        }.toString()

    fun response(code: String): String = JSONObject().put("version", 1).put("result", code)
        .put("time", System.currentTimeMillis()).toString()
}
