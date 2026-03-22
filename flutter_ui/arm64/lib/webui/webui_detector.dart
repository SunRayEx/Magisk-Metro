import 'dart:io';
import 'package:flutter/foundation.dart';

/// WebUI type enumeration
enum WebUIType {
  /// Standard: webroot/index.html is the actual WebUI entry
  standard,

  /// Redirect: index.html contains redirect code to module's own HTTP service
  redirect,

  /// Unknown type
  unknown,
}

/// WebUI detection result
class WebUIDetectionResult {
  final WebUIType type;
  final String? redirectUrl;
  final int? redirectPort;
  final String? redirectPath;
  final String? indexContentSnippet;

  const WebUIDetectionResult({
    required this.type,
    this.redirectUrl,
    this.redirectPort,
    this.redirectPath,
    this.indexContentSnippet,
  });
}

/// WebUI detector for detecting module WebUI type
class WebUIDetector {
  static Future<WebUIDetectionResult> detect(String modulePath) async {
    final indexPath = '$modulePath/webroot/index.html';

    final indexExists = await _fileExists(indexPath);
    if (!indexExists) {
      debugPrint('[WebUIDetector] No index.html found at $indexPath');
      return const WebUIDetectionResult(type: WebUIType.unknown);
    }

    final content = await _readFile(indexPath);
    if (content == null || content.isEmpty) {
      debugPrint('[WebUIDetector] index.html is empty');
      return const WebUIDetectionResult(type: WebUIType.unknown);
    }

    final snippetPreview = content.length > 200
        ? '${content.substring(0, 200)}...'
        : content;

    debugPrint('[WebUIDetector] Analyzing index.html content...');

    final hasBodyTag = _hasBodyTag(content);
    final hasActualContent = _hasActualContent(content);

    if (hasBodyTag && hasActualContent) {
      final hasRedirect = _hasRedirectCode(content);

      if (!hasRedirect) {
        debugPrint('[WebUIDetector] Detected STANDARD type');
        return WebUIDetectionResult(
          type: WebUIType.standard,
          indexContentSnippet: snippetPreview,
        );
      }
    }

    // Detect port pattern like :9090, :8080
    final portPattern = RegExp(r':(\d{4,5})(?:/|\s|''|")');
    final portMatch = portPattern.firstMatch(content);

    if (portMatch != null) {
      final port = int.tryParse(portMatch.group(1) ?? '');
      if (port != null && port > 0 && port < 65536) {
        final pathPattern = RegExp(r':\d+(/[^\s'']*)?');
        final pathMatch = pathPattern.firstMatch(content);
        final path = pathMatch?.group(1) ?? '/';

        final redirectUrl = 'http://127.0.0.1:$port$path';
        debugPrint('[WebUIDetector] Detected REDIRECT type: $redirectUrl');

        return WebUIDetectionResult(
          type: WebUIType.redirect,
          redirectUrl: redirectUrl,
          redirectPort: port,
          redirectPath: path,
          indexContentSnippet: snippetPreview,
        );
      }
    }

    // Detect JavaScript redirect
    final jsRedirectPattern = RegExp(
      r"window\.location(?:\.href)?\s*=\s*['"'"'"]([^'"'"'"]+)['"'"'"]",
      caseSensitive: false,
    );

    final jsMatch = jsRedirectPattern.firstMatch(content);
    if (jsMatch != null) {
      final targetUrl = jsMatch.group(1) ?? '';
      debugPrint('[WebUIDetector] JS redirect found: $targetUrl');

      final portPathPattern = RegExp(r':(\d+)(/\S*)?');
      final portPathMatch = portPathPattern.firstMatch(targetUrl);

      if (portPathMatch != null) {
        final port = int.tryParse(portPathMatch.group(1) ?? '');
        final path = portPathMatch.group(2) ?? '/';

        if (port != null && port > 0) {
          final redirectUrl = 'http://127.0.0.1:$port$path';
          debugPrint('[WebUIDetector] Detected REDIRECT from JS: $redirectUrl');

          return WebUIDetectionResult(
            type: WebUIType.redirect,
            redirectUrl: redirectUrl,
            redirectPort: port,
            redirectPath: path,
            indexContentSnippet: snippetPreview,
          );
        }
      }
    }

    // Detect meta refresh tag
    final metaRefreshPattern = RegExp(
      r'<meta[^>]+http-equiv=["'"'"'?refresh["'"'"'?[^>]+content=["'"'"'?\d+;\s*url=(\S+)',
      caseSensitive: false,
    );

    final metaMatch = metaRefreshPattern.firstMatch(content);
    if (metaMatch != null) {
      final targetUrl = metaMatch.group(1) ?? '';
      debugPrint('[WebUIDetector] Meta refresh found: $targetUrl');

      final portPathPattern = RegExp(r':(\d+)(/\S*)?');
      final portPathMatch = portPathPattern.firstMatch(targetUrl);

      if (portPathMatch != null) {
        final port = int.tryParse(portPathMatch.group(1) ?? '');
        final path = portPathMatch.group(2) ?? '/';

        if (port != null && port > 0) {
          final redirectUrl = 'http://127.0.0.1:$port$path';
          debugPrint('[WebUIDetector] Detected REDIRECT from meta: $redirectUrl');

          return WebUIDetectionResult(
            type: WebUIType.redirect,
            redirectUrl: redirectUrl,
            redirectPort: port,
            redirectPath: path,
            indexContentSnippet: snippetPreview,
          );
        }
      }
    }

    // Detect iframe src
    final iframePattern = RegExp(
      r'<iframe[^>]+src=["'"'"']([^"'"'"']+)["'"'"']',
      caseSensitive: false,
    );

    final iframeMatch = iframePattern.firstMatch(content);
    if (iframeMatch != null) {
      final srcUrl = iframeMatch.group(1) ?? '';
      debugPrint('[WebUIDetector] Iframe src found: $srcUrl');

      final portPathPattern = RegExp(r':(\d+)(/\S*)?');
      final portPathMatch = portPathPattern.firstMatch(srcUrl);

      if (portPathMatch != null) {
        final port = int.tryParse(portPathMatch.group(1) ?? '');
        final path = portPathMatch.group(2) ?? '/';

        if (port != null && port > 0) {
          final redirectUrl = 'http://127.0.0.1:$port$path';
          debugPrint('[WebUIDetector] Detected REDIRECT from iframe: $redirectUrl');

          return WebUIDetectionResult(
            type: WebUIType.redirect,
            redirectUrl: redirectUrl,
            redirectPort: port,
            redirectPath: path,
            indexContentSnippet: snippetPreview,
          );
        }
      }
    }

    debugPrint('[WebUIDetector] Defaulting to STANDARD type');
    return WebUIDetectionResult(
      type: WebUIType.standard,
      indexContentSnippet: snippetPreview,
    );
  }

  static Future<bool> _fileExists(String path) async {
    try {
      final result = await Process.run('su', ['-c', 'test -f "$path" && echo "exists"']);
      return result.stdout.toString().trim() == 'exists';
    } catch (e) {
      debugPrint('[WebUIDetector] Error checking file existence: $e');
      return false;
    }
  }

  static Future<String?> _readFile(String path) async {
    try {
      final result = await Process.run('su', ['-c', 'cat "$path"']);
      if (result.exitCode == 0) {
        return result.stdout.toString();
      }
      return null;
    } catch (e) {
      debugPrint('[WebUIDetector] Error reading file: $e');
      return null;
    }
  }

  static bool _hasBodyTag(String content) {
    return RegExp(r'<body', caseSensitive: false).hasMatch(content);
  }

  static bool _hasActualContent(String content) {
    final visibleElements = RegExp(
      r'<(div|span|p|h[1-6]|button|input|table|ul|ol|form|a|img|script|style)[\s>]',
      caseSensitive: false,
    );
    return visibleElements.hasMatch(content);
  }

  static bool _hasRedirectCode(String content) {
    final redirectPatterns = [
      RegExp(r'window\.location', caseSensitive: false),
      RegExp(r'http-equiv=["'"'"'?refresh', caseSensitive: false),
      RegExp(r'<iframe[^>]+src=', caseSensitive: false),
      RegExp(r':\d{4,5}/', caseSensitive: false),
    ];

    for (final pattern in redirectPatterns) {
      if (pattern.hasMatch(content)) {
        return true;
      }
    }
    return false;
  }

  static Future<bool> hasWebUI(String modulePath) async {
    final indexPath = '$modulePath/webroot/index.html';
    return await _fileExists(indexPath);
  }

  static Future<String?> getWebUIUrl(String modulePath, {int localPort = 0}) async {
    final result = await detect(modulePath);

    switch (result.type) {
      case WebUIType.standard:
        if (localPort > 0) {
          return 'http://127.0.0.1:$localPort/index.html';
        }
        return 'file://$modulePath/webroot/index.html';

      case WebUIType.redirect:
        return result.redirectUrl;

      case WebUIType.unknown:
        return null;
    }
  }
}
