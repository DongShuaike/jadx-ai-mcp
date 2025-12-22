package com.zin.jadxaimcp.utils;

import java.util.*;

import sl4fj.logger.Logger;
import sl4fj.logger.LoggerFactory;

import io.javalin.http.Context;

public class Helpers {
    public static void handleError(Context ctx, String error_response, Exception e, Logger logger) {
        logger.error("JADX AI MCP Error: " + e.getMessage(), e);
        ctx.status(500).json(Map.of("error", error_response));
    }
}
