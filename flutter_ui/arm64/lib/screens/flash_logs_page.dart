import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import '../providers/dashboard_providers.dart';

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

  @override
  void initState() {
    super.initState();
    _startExecution();
  }

  void _startExecution() async {
    setState(() {
      _isRunning = true;
      _logs.clear();
      _addLog('Starting ${widget.title}...');
    });

    try {
      final result = await widget.onExecute();
      if (result) {
        _addLog('Operation completed successfully!');
        _isSuccess = true;
      } else {
        _addLog('Operation failed!');
        _isSuccess = false;
      }
    } catch (e) {
      _addLog('Error: $e');
      _isSuccess = false;
    } finally {
      setState(() {
        _isRunning = false;
        _isCompleted = true;
      });
    }
  }

  void _addLog(String message) {
    setState(() {
      _logs.add('[${DateTime.now().toIso8601String().split('.')[0]}] $message');
    });
  }

  @override
  Widget build(BuildContext context) {
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
                padding: const EdgeInsets.all(8),
                itemCount: _logs.length,
                itemBuilder: (context, index) {
                  final log = _logs[index];
                  final isError = log.contains('Error') || log.contains('failed');
                  final isWarning = log.contains('Warning') || log.contains('warn');
                  Color textColor = Colors.white;
                  
                  if (isError) {
                    textColor = Colors.red;
                  } else if (isWarning) {
                    textColor = Colors.yellow;
                  } else if (log.contains('success') || log.contains('completed')) {
                    textColor = Colors.green;
                  }

                  return Container(
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
                        _isSuccess ? 'Operation completed successfully!' : 'Operation failed!',
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
                      child: const Text('Close'),
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
                    const Text('Operation in progress...'),
                    const Spacer(),
                    TextButton(
                      onPressed: () {
                        // Cancel operation logic would go here
                        Navigator.pop(context);
                      },
                      child: const Text('Cancel'),
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
