import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/services.dart';
import 'package:google_fonts/google_fonts.dart';
import '../providers/dashboard_providers.dart';
import '../l10n/app_localizations.dart';
import '../services/android_data_service.dart';

class FlashLogsPage extends ConsumerStatefulWidget {
  final String title;
  final Future<bool> Function() onExecute;

  const FlashLogsPage({
    super.key,
    required this.title,
    required this.onExecute,
  });

  @override
  ConsumerState<FlashLogsPage> createState() => _FlashLogsPageState();
}

class _FlashLogsPageState extends ConsumerState<FlashLogsPage> {
  final List<String> _logs = [];
  bool _isRunning = false;
  bool _isCompleted = false;
  bool _isSuccess = false;
  StreamSubscription<String>? _logSubscription;
  final ScrollController _scrollController = ScrollController();
  final Completer<void> _logStreamCompleter = Completer<void>();

  @override
  void initState() {
    super.initState();
    // 延迟执行以允许翻转动画完成
    // FlipPageRoute 动画时长为 400ms，额外等待 200ms 确保动画完全结束
    WidgetsBinding.instance.addPostFrameCallback((_) {
      Future.delayed(const Duration(milliseconds: 600), () {
        if (mounted) {
          _startExecution();
        }
      });
    });
  }

  @override
  void dispose() {
    _logSubscription?.cancel();
    _scrollController.dispose();
    super.dispose();
  }

  void _scrollToBottom() {
    if (_scrollController.hasClients) {
      Future.delayed(const Duration(milliseconds: 100), () {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 200),
          curve: Curves.easeOut,
        );
      });
    }
  }

  Future<void> _startExecution() async {
    final localizations = AppLocalizations.of(context)!;
    setState(() {
      _isRunning = true;
      _logs.clear();
      
      // Add MagisKube ASCII art banner
      _addMagisKubeBanner();
      _addLog(localizations.starting + ' ${widget.title}...');
    });

    // Subscribe to log stream with completer to ensure it's established
    bool streamListening = false;
    _logSubscription = AndroidDataService.getLogcatStream().listen(
      (log) {
        _addLog(log, timestamp: false);
        _scrollToBottom();
        // Mark stream as listening on first log received
        if (!streamListening) {
          streamListening = true;
          if (!_logStreamCompleter.isCompleted) {
            _logStreamCompleter.complete();
          }
        }
      },
      onDone: () {
        if (!_logStreamCompleter.isCompleted) {
          _logStreamCompleter.complete();
        }
      },
      onError: (error) {
        if (!_logStreamCompleter.isCompleted) {
          _logStreamCompleter.complete();
        }
      },
    );
    
    // Wait for log stream to be established with timeout
    try {
      await _logStreamCompleter.future.timeout(const Duration(milliseconds: 500));
    } catch (e) {
      // Continue anyway even if stream doesn't establish
    }

    try {
      final result = await widget.onExecute();
      if (result) {
        _addLog(localizations.operationCompleted);
        _isSuccess = true;
      } else {
        _addLog(localizations.operationFailed);
        _isSuccess = false;
      }
    } catch (e) {
      _addLog('${localizations.error}: $e');
      _isSuccess = false;
    } finally {
      await _logSubscription?.cancel();
      setState(() {
        _isRunning = false;
        _isCompleted = true;
      });
    }
  }

  void _addMagisKubeBanner() {
    final banner = '''
            __     __             __   ___ 
 |\/|  /\  / _` | /__` |__/ |  | |__) |__  
 |  | /~~\ \__> | .__/ |  \ \__/ |__) |___ 
                                           
''';
    final lines = banner.split('\n');
    for (final line in lines) {
      if (line.trim().isNotEmpty) {
        _logs.add(line);
      }
    }
  }

  void _addLog(String message, {bool timestamp = true}) {
    setState(() {
      if (timestamp) {
        _logs.add('[${DateTime.now().toIso8601String().split('.')[0]}] $message');
      } else {
        _logs.add(message);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final localizations = AppLocalizations.of(context)!;
    final isDark = ref.watch(themeProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final widgetColor = AppTheme.getTileWidgetColor(0, tileColorIndex, isDark);

    return Scaffold(
      backgroundColor: AppTheme.getBackground(isDark),
      body: SafeArea(
        child: Column(
          children: [
            _buildHeader(context, widget.title, isDark),
            Expanded(
              child: ListView.builder(
                controller: _scrollController,
                padding: const EdgeInsets.all(8),
                itemCount: _logs.length,
                itemBuilder: (context, index) {
                  final log = _logs[index];
                  final isError = log.contains('[ERROR]') || log.contains('Error') || log.contains('failed');
                  final isWarning = log.contains('[WARN]') || log.contains('Warning') || log.contains('warn');
                  final isSuccess = log.contains('[INFO]') && (log.contains('success') || log.contains('completed'));
                  Color textColor = AppTheme.getListItemFont(isDark);
                  
                  if (isError) {
                    textColor = Colors.red;
                  } else if (isWarning) {
                    textColor = Colors.yellow;
                  } else if (isSuccess) {
                    textColor = Colors.green;
                  }

                  return RepaintBoundary( // ← 这里改了: 隔离刷机日志的大量文本重绘
                    child: Container(
                      margin: const EdgeInsets.only(bottom: 2),
                      padding: const EdgeInsets.all(8),
                      decoration: BoxDecoration(
                        color: AppTheme.getListItem(isDark),
                        borderRadius: BorderRadius.circular(4),
                      ),
                      child: Text(
                        log,
                        style: GoogleFonts.poppins(
                          fontSize: 12,
                          color: textColor,
                        ),
                      ),
                    ),
                  );
                },
              ),
            ),
            if (_isCompleted)
              Container(
                padding: const EdgeInsets.all(16),
                color: AppTheme.getTile(isDark),
                child: Row(
                  children: [
                    Expanded(
                      child: Text(
                        _isSuccess ? localizations.operationCompleted : localizations.operationFailed,
                        style: GoogleFonts.poppins(
                          fontWeight: FontWeight.w900,
                          fontSize: 16,
                          color: _isSuccess ? Colors.green : Colors.red,
                        ),
                        textAlign: TextAlign.center,
                      ),
                    ),
                    ElevatedButton(
                      onPressed: () {
                        Navigator.pop(context);
                      },
                      style: ElevatedButton.styleFrom(
                        backgroundColor: widgetColor,
                        foregroundColor: Colors.white,
                      ),
                      child: Text(localizations.close),
                    ),
                  ],
                ),
              ),
            if (_isRunning)
              Container(
                padding: const EdgeInsets.all(16),
                color: AppTheme.getTile(isDark),
                child: Row(
                  children: [
                    const CircularProgressIndicator(),
                    const SizedBox(width: 16),
                    Text(localizations.operationInProgress),
                    const Spacer(),
                    TextButton(
                      onPressed: () {
                        // Cancel operation logic would go here
                        Navigator.pop(context);
                      },
                      child: Text(localizations.cancel),
                    ),
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context, String title, bool isDark) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 12),
      color: AppTheme.getTile(isDark),
      child: Row(
        children: [
          if (!_isCompleted && !_isRunning)
            GestureDetector(
              onTap: () => Navigator.pop(context),
              child: Container(
                padding: const EdgeInsets.all(8),
                child: Icon(Icons.chevron_left,
                    color: AppTheme.getFont(isDark), size: 28),
              ),
            ),
          Expanded(
            child: Text(
              title,
              style: GoogleFonts.poppins(
                fontWeight: FontWeight.w900,
                fontSize: 20,
                color: AppTheme.getFont(isDark),
              ),
            ),
          ),
          if (_isCompleted || _isRunning)
            Container(), // Empty container to maintain alignment
        ],
      ),
    );
  }
}
