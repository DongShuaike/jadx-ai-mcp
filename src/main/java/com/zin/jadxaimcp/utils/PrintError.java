package com.zin.jadxaimcp.utils;

import java.util.*;

import sl4fj.logger.Logger;
import sl4fj.logger.LoggerFactory;

import io.javalin.http.Context;

public class PrintError {
    public static void handleError(Context ctx, String error_response, Exception e, Logger logger) {
        logger.error("JADX AI MCP Error: " + error_response, e);
        ctx.status(500).json(Map.of("error", error_response));
    }

    public static void handleError(Context ctx, int status, String error_response, Logger logger) {
        logger.error("JADX AI MCP Error: " + error_response);
        ctx.status(status).json(Map.of("error", error_response));
    }
}
