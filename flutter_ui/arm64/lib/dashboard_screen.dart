import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'providers/dashboard_providers.dart';
import 'navigation/flip_page_route.dart';
import 'screens/secondary_pages.dart';
import 'l10n/app_localizations.dart';

class DashboardScreen extends ConsumerStatefulWidget {
  const DashboardScreen({super.key});

  @override
  ConsumerState<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends ConsumerState<DashboardScreen> {
  Timer? _carouselTimer;
  int _modulesCarouselIndex = 0;
  int _appsCarouselIndex = 0;
  
  // Drag state
  String? _draggingTileId;
  Offset _dragOffset = Offset.zero;
  int? _dragTargetRow;
  int? _dragTargetCol;
  
  // Resize state
  String? _resizingTileId;
  int _originalWidth = 1;
  int _originalHeight = 1;
  int _previewWidth = 1;
  int _previewHeight = 1;
  

  @override
  void initState() {
    super.initState();
    _startCarouselTimer();
  }

  @override
  void dispose() {
    _carouselTimer?.cancel();
    super.dispose();
  }

  void _startCarouselTimer() {
    _carouselTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      final modules = ref.read(modulesProvider);
      final apps = ref.read(appsProvider);
      
      // Filter enabled modules
      final enabledModules = modules.where((m) => m.isEnabled).toList();
      final enabledApps = apps.where((a) => a.hasRootAccess).toList();
      
      setState(() {
        // Update modules carousel index
        if (enabledModules.length > 3) {
          _modulesCarouselIndex = (_modulesCarouselIndex + 1) % enabledModules.length;
        } else {
          _modulesCarouselIndex = 0;
        }
        
        // Update apps carousel index
        if (enabledApps.length > 3) {
          _appsCarouselIndex = (_appsCarouselIndex + 1) % enabledApps.length;
        } else {
          _appsCarouselIndex = 0;
        }
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    final isDark = ref.watch(themeProvider);
    final isLocked = ref.watch(lockModeProvider);
    final tileLayout = ref.watch(tileLayoutProvider);

    return TickerMode(
      enabled: ModalRoute.of(context)?.isCurrent ?? true,
      child: Scaffold(
        backgroundColor: Colors.black,
        body: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(2.0),
            child: Column(
              children: [
                _buildTopBar(context, ref, isDark, isLocked),
                Expanded(
                  child: _buildTileGrid(context, ref, isDark, tileLayout, isLocked),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildTopBar(BuildContext context, WidgetRef ref, bool isDark, bool isLocked) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4.0, vertical: 2.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.end,
        children: [
          // Settings button only
          GestureDetector(
            onTap: () => _showSettings(context, ref),
            child: Container(
              padding: const EdgeInsets.all(8),
              child: Icon(
                Icons.settings,
                color: Colors.white,
                size: 24,
              ),
            ),
          ),
        ],
      ),
    );
  }

  void _showSettings(BuildContext context, WidgetRef ref) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (context) => SettingsSheet(
        onThemeTap: () {
          Navigator.pop(context);
          _navigateTo(context, '/theme');
        },
      ),
    );
  }

  void _navigateTo(BuildContext context, String route) {
    Widget page;
    switch (route) {
      case '/magisk':
        page = const MagiskManagerPage();
        break;
      case '/settings':
        page = const SettingsPage();
        break;
      case '/modules':
        page = const ModulesPage();
        break;
      case '/apps':
        page = const AppsPage();
        break;
      case '/denylist':
        page = const DenyListPage();
        break;
      case '/logs':
        page = const LogsPage();
        break;
      case '/contributors':
        page = const ContributorsPage();
        break;
      case '/theme':
        page = const ThemePage();
        break;
      default:
        return;
    }
    Navigator.push(context, FlipPageRoute(page: page));
  }

  /// Build the tile grid with drag and resize support
  Widget _buildTileGrid(BuildContext context, WidgetRef ref, bool isDark, List<TileConfig> tileLayout, bool isLocked) {
    // Grid configuration: 3 columns, dynamic rows based on tile positions
    const int gridColumns = 3;
    const double cellSpacing = 2.0;
    
    // Calculate grid height based on tiles
    int maxRow = 0;
    for (final tile in tileLayout) {
      maxRow = maxRow > (tile.row + tile.height) ? maxRow : (tile.row + tile.height);
    }
    final gridRows = maxRow;
    
    return LayoutBuilder(
      builder: (context, constraints) {
        final cellWidth = (constraints.maxWidth - cellSpacing * (gridColumns - 1)) / gridColumns;
        final cellHeight = (constraints.maxHeight - cellSpacing * (gridRows - 1)) / gridRows;
        
        return Stack(
          children: [
            // Grid background (for visual feedback during drag)
            if (!isLocked)
              ...List.generate(gridRows * gridColumns, (index) {
                final row = index ~/ gridColumns;
                final col = index % gridColumns;
                return Positioned(
                  left: col * (cellWidth + cellSpacing),
                  top: row * (cellHeight + cellSpacing),
                  width: cellWidth,
                  height: cellHeight,
                  child: Container(
                    decoration: BoxDecoration(
                      border: Border.all(
                        color: _dragTargetRow == row && _dragTargetCol == col
                            ? Colors.green.withOpacity(0.5)
                            : Colors.grey.withOpacity(0.2),
                        width: 1,
                      ),
                      borderRadius: BorderRadius.circular(4),
                    ),
                  ),
                );
              }),
            
            // Tiles
            ...tileLayout.map((tile) {
              final isDragging = _draggingTileId == tile.id;
              final isResizing = _resizingTileId == tile.id;
              
              // Calculate position
              double left = tile.col * (cellWidth + cellSpacing);
              double top = tile.row * (cellHeight + cellSpacing);
              double width = tile.width * cellWidth + (tile.width - 1) * cellSpacing;
              double height = tile.height * cellHeight + (tile.height - 1) * cellSpacing;
              
              // If dragging, show preview at target position
              if (isDragging && _dragTargetRow != null && _dragTargetCol != null) {
                left = _dragTargetCol! * (cellWidth + cellSpacing);
                top = _dragTargetRow! * (cellHeight + cellSpacing);
              }
              
              // If resizing, show preview size
              if (isResizing) {
                width = _previewWidth * cellWidth + (_previewWidth - 1) * cellSpacing;
                height = _previewHeight * cellHeight + (_previewHeight - 1) * cellSpacing;
              }
              
              return Positioned(
                left: left,
                top: top,
                width: width,
                height: height,
                child: _buildDraggableTile(
                  context,
                  ref,
                  isDark,
                  tile,
                  isLocked,
                  isDragging,
                  isResizing,
                  cellWidth,
                  cellHeight,
                ),
              );
            }),
            
            // Dragging tile overlay (follows finger)
            if (_draggingTileId != null && _dragOffset != Offset.zero)
              Positioned(
                left: _dragOffset.dx,
                top: _dragOffset.dy,
                child: _buildTileContent(
                  context,
                  ref,
                  isDark,
                  tileLayout.firstWhere((t) => t.id == _draggingTileId!),
                  true, // isDragging
                ),
              ),
          ],
        );
      },
    );
  }

  Widget _buildDraggableTile(
    BuildContext context,
    WidgetRef ref,
    bool isDark,
    TileConfig tile,
    bool isLocked,
    bool isDragging,
    bool isResizing,
    double cellWidth,
    double cellHeight,
  ) {
    // When unlocked, tiles are draggable directly (no edit mode needed)
    final canDrag = !isLocked;
    
    if (!canDrag) {
      // Locked mode - just show the tile, tap to navigate
      return GestureDetector(
        onTap: () => _onTileTap(context, tile),
        child: _buildTileContent(context, ref, isDark, tile, false),
      );
    }
    
    // Unlocked mode - tile is draggable and resizable via long press
    return GestureDetector(
      onLongPressStart: (details) => _startDrag(tile, details, cellWidth, cellHeight, context),
      onLongPressMoveUpdate: (details) => _updateDrag(details, cellWidth, cellHeight, ref, context),
      onLongPressEnd: (details) => _endDrag(ref),
      onTap: () => _onTileTap(context, tile),
      child: Stack(
        children: [
          // Tile content
          _buildTileContent(context, ref, isDark, tile, isDragging),
          
          // Resize handles (show when unlocked and not dragging)
          if (!isDragging)
            ..._buildResizeHandles(tile, cellWidth, cellHeight, ref, context),
          
          // Drag indicator (show when unlocked)
          Positioned(
            top: 4,
            right: 4,
            child: Container(
              padding: const EdgeInsets.all(4),
              decoration: BoxDecoration(
                color: Colors.black54,
                borderRadius: BorderRadius.circular(4),
              ),
              child: const Icon(
                Icons.drag_indicator,
                color: Colors.white70,
                size: 16,
              ),
            ),
          ),
        ],
      ),
    );
  }

  List<Widget> _buildResizeHandles(TileConfig tile, double cellWidth, double cellHeight, WidgetRef ref, BuildContext gridContext) {
    return [
      // Bottom-right corner resize handle
      Positioned(
        bottom: 0,
        right: 0,
        child: GestureDetector(
          onPanStart: (details) => _startResize(tile),
          onPanUpdate: (details) => _updateResize(details, cellWidth, cellHeight, ref, gridContext),
          onPanEnd: (details) => _endResize(ref),
          child: Container(
            width: 24,
            height: 24,
            decoration: BoxDecoration(
              color: Colors.blue.withOpacity(0.7),
              borderRadius: const BorderRadius.only(
                bottomRight: Radius.circular(4),
              ),
            ),
            child: const Icon(
              Icons.aspect_ratio,
              color: Colors.white,
              size: 16,
            ),
          ),
        ),
      ),
    ];
  }

  void _startDrag(TileConfig tile, LongPressStartDetails details, double cellWidth, double cellHeight, BuildContext gridContext) {
    // Get the grid's RenderBox for position calculation
    final RenderBox? gridRenderBox = gridContext.findRenderObject() as RenderBox?;
    if (gridRenderBox == null) return;
    
    final localPosition = gridRenderBox.globalToLocal(details.globalPosition);
    const cellSpacing = 2.0;
    
    // Calculate initial target position
    int targetCol = (localPosition.dx / (cellWidth + cellSpacing)).floor().clamp(0, 2);
    int targetRow = (localPosition.dy / (cellHeight + cellSpacing)).floor().clamp(0, 4);
    
    setState(() {
      _draggingTileId = tile.id;
      _dragOffset = localPosition;
      _dragTargetRow = targetRow;
      _dragTargetCol = targetCol;
    });
  }

  void _updateDrag(LongPressMoveUpdateDetails details, double cellWidth, double cellHeight, WidgetRef ref, BuildContext gridContext) {
    final tileLayout = ref.read(tileLayoutProvider);
    final draggingTile = tileLayout.firstWhere((t) => t.id == _draggingTileId!);
    
    // Get the grid's RenderBox for position calculation
    final RenderBox? gridRenderBox = gridContext.findRenderObject() as RenderBox?;
    if (gridRenderBox == null) return;
    
    final localPosition = gridRenderBox.globalToLocal(details.globalPosition);
    
    // Calculate target row and column
    const cellSpacing = 2.0;
    int targetCol = (localPosition.dx / (cellWidth + cellSpacing)).floor().clamp(0, 2);
    int targetRow = (localPosition.dy / (cellHeight + cellSpacing)).floor().clamp(0, 4);
    
    // Check if position is available
    final layoutNotifier = ref.read(tileLayoutProvider.notifier);
    bool canMove = true;
    
    // Check if the tile's new position would overlap with others
    for (int r = targetRow; r < targetRow + draggingTile.height && canMove; r++) {
      for (int c = targetCol; c < targetCol + draggingTile.width && canMove; c++) {
        if (c >= 3) canMove = false; // Out of bounds
        else if (layoutNotifier.isPositionOccupied(r, c, _draggingTileId!)) {
          canMove = false;
        }
      }
    }
    
    setState(() {
      _dragOffset = localPosition;
    });
    
    if (canMove && (_dragTargetRow != targetRow || _dragTargetCol != targetCol)) {
      setState(() {
        _dragTargetRow = targetRow;
        _dragTargetCol = targetCol;
      });
    }
  }

  void _endDrag(WidgetRef ref) {
    if (_draggingTileId != null && _dragTargetRow != null && _dragTargetCol != null) {
      final layoutNotifier = ref.read(tileLayoutProvider.notifier);
      layoutNotifier.moveTile(_draggingTileId!, _dragTargetRow!, _dragTargetCol!);
      layoutNotifier.saveLayout();
    }
    
    setState(() {
      _draggingTileId = null;
      _dragOffset = Offset.zero;
      _dragTargetRow = null;
      _dragTargetCol = null;
    });
  }

  void _startResize(TileConfig tile) {
    setState(() {
      _resizingTileId = tile.id;
      _originalWidth = tile.width;
      _originalHeight = tile.height;
      _previewWidth = tile.width;
      _previewHeight = tile.height;
    });
  }

  void _updateResize(DragUpdateDetails details, double cellWidth, double cellHeight, WidgetRef ref, BuildContext gridContext) {
    // Calculate size change based on global position
    final RenderBox? gridRenderBox = gridContext.findRenderObject() as RenderBox?;
    if (gridRenderBox == null) return;
    
    final localPosition = gridRenderBox.globalToLocal(details.globalPosition);
    const cellSpacing = 2.0;
    
    // Get current tile position
    final tileLayout = ref.read(tileLayoutProvider);
    final resizingTile = tileLayout.firstWhere((t) => t.id == _resizingTileId!);
    
    // Calculate new width and height based on position relative to tile's start
    final tileEndX = (resizingTile.col + 1) * (cellWidth + cellSpacing);
    final tileEndY = (resizingTile.row + 1) * (cellHeight + cellSpacing);
    
    // Calculate how many cells the drag extends beyond the original 1x1
    final deltaX = localPosition.dx - tileEndX;
    final deltaY = localPosition.dy - tileEndY;
    
    int newWidth = 1 + (deltaX / (cellWidth + cellSpacing)).round().clamp(0, 2);
    int newHeight = 1 + (deltaY / (cellHeight + cellSpacing)).round().clamp(0, 2);
    
    // Maximum size is 3x3
    newWidth = newWidth.clamp(1, 3);
    newHeight = newHeight.clamp(1, 3);
    
    if (_previewWidth != newWidth || _previewHeight != newHeight) {
      setState(() {
        _previewWidth = newWidth;
        _previewHeight = newHeight;
      });
    }
  }

  void _endResize(WidgetRef ref) {
    if (_resizingTileId != null) {
      final layoutNotifier = ref.read(tileLayoutProvider.notifier);
      layoutNotifier.resizeTile(_resizingTileId!, _previewWidth, _previewHeight);
      layoutNotifier.saveLayout();
    }
    
    setState(() {
      _resizingTileId = null;
    });
  }

  void _onTileTap(BuildContext context, TileConfig tile) {
    // When unlocked, don't navigate on tap (use long press to drag)
    if (!ref.read(lockModeProvider)) return;
    
    switch (tile.type) {
      case 'magisk':
        _navigateTo(context, '/magisk');
        break;
      case 'settings':
        _navigateTo(context, '/settings');
        break;
      case 'modules':
        _navigateTo(context, '/modules');
        break;
      case 'apps':
        _navigateTo(context, '/apps');
        break;
      case 'logs':
        _navigateTo(context, '/logs');
        break;
      case 'contributor':
        _navigateTo(context, '/contributors');
        break;
      case 'sponsor':
        _navigateTo(context, '/contributors');
        break;
    }
  }

  Widget _buildTileContent(BuildContext context, WidgetRef ref, bool isDark, TileConfig tile, bool isDragging) {
    // Add opacity when dragging to show it's being moved
    final opacity = isDragging ? 0.7 : 1.0;
    
    return Opacity(
      opacity: opacity,
      child: _getTileWidget(context, ref, isDark, tile),
    );
  }

  Widget _getTileWidget(BuildContext context, WidgetRef ref, bool isDark, TileConfig tile) {
    switch (tile.type) {
      case 'magisk':
        return _buildMagiskContent(context, ref, tile);
      case 'settings':
        return _buildSettingsContent(context, ref, tile);
      case 'modules':
        return _buildModulesContent(context, ref, tile);
      case 'apps':
        return _buildAppsContent(context, ref, tile);
      case 'logs':
        return _buildLogsContent(context, ref, tile);
      case 'contributor':
        return _buildContributorContent(context, ref, tile);
      case 'sponsor':
        return _buildSponsorContent(context, ref, tile);
      default:
        return Container(color: Colors.grey);
    }
  }

  // Content builders for each tile type
  Widget _buildMagiskContent(BuildContext context, WidgetRef ref, TileConfig tile) {
    final status = ref.watch(magiskStatusProvider);
    final isDarkMode = ref.watch(themeProvider);
    final colorIndex = ref.watch(tileColorProvider);
    final customColors = ref.watch(customTileColorsProvider);
    final localizations = AppLocalizations.of(context)!;
    
    final tileColor = AppTheme.getTileWithCustomColors(0, colorIndex, isDarkMode, customColors);
    final textColor = Colors.black87;
    
    return Container(
      color: tileColor,
      padding: const EdgeInsets.all(16.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: tile.height > 1 ? 60 : 40,
                height: tile.height > 1 ? 60 : 40,
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(
                  Icons.face,
                  size: tile.height > 1 ? 45 : 30,
                  color: Colors.black,
                ),
              ),
              const SizedBox(width: 12),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Magisk ${status.versionCode}',
                    style: GoogleFonts.poppins(
                      fontWeight: FontWeight.w900,
                      fontSize: tile.height > 1 ? 24 : 18,
                      color: textColor,
                    ),
                  ),
                  Text(
                    '[${status.isRooted ? localizations.enabled : localizations.disabled}]',
                    style: GoogleFonts.poppins(
                      fontWeight: FontWeight.w600,
                      fontSize: tile.height > 1 ? 16 : 12,
                      color: textColor,
                    ),
                  ),
                ],
              ),
            ],
          ),
          if (tile.height > 1) const Spacer(),
          if (tile.height > 1)
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildStatusRow(localizations.root, status.isRooted, localizations, textColor),
                _buildStatusRow(localizations.zygisk, status.isZygiskEnabled, localizations, textColor),
                if (tile.height >= 2)
                  _buildStatusRow(localizations.ramdisk, status.isRamdiskLoaded, localizations, textColor),
              ],
            ),
        ],
      ),
    );
  }

  Widget _buildSettingsContent(BuildContext context, WidgetRef ref, TileConfig tile) {
    final isDarkMode = ref.watch(themeProvider);
    final colorIndex = ref.watch(tileColorProvider);
    final customColors = ref.watch(customTileColorsProvider);
    final localizations = AppLocalizations.of(context)!;
    
    final tileColor = AppTheme.getTileWithCustomColors(1, colorIndex, isDarkMode, customColors);
    final textColor = Colors.black87;
    
    return Container(
      color: tileColor,
      padding: const EdgeInsets.all(16.0),
      child: Align(
        alignment: Alignment.topLeft,
        child: Text(
          localizations.settings,
          style: GoogleFonts.poppins(
            fontWeight: FontWeight.w900,
            fontSize: tile.height > 1 && tile.width > 1 ? 28 : 20,
            color: textColor,
          ),
        ),
      ),
    );
  }

  Widget _buildModulesContent(BuildContext context, WidgetRef ref, TileConfig tile) {
    final status = ref.watch(magiskStatusProvider);
    final isDarkMode = ref.watch(themeProvider);
    final colorIndex = ref.watch(tileColorProvider);
    final customColors = ref.watch(customTileColorsProvider);
    final modules = ref.watch(modulesProvider);
    final localizations = AppLocalizations.of(context)!;
    
    final tileColor = AppTheme.getTileWithCustomColors(3, colorIndex, isDarkMode, customColors);
    final textColor = Colors.black87;
    
    final enabledModules = modules.where((m) => m.isEnabled).toList();
    
    return Opacity(
      opacity: status.isRooted ? 1.0 : 0.5,
      child: Container(
        color: tileColor,
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              localizations.modules,
              style: GoogleFonts.poppins(
                fontWeight: FontWeight.w900,
                fontSize: tile.height > 1 ? 24 : 18,
                color: textColor,
              ),
            ),
            if (tile.height > 1) const SizedBox(height: 8),
            if (tile.height > 1)
              Expanded(
                child: _CarouselItemDisplay(
                  items: enabledModules.map((m) => m.name).toList(),
                  startIndex: _modulesCarouselIndex,
                  maxItems: tile.height > 1 ? 3 : 1,
                  textColor: textColor,
                ),
              ),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                Text(
                  '${enabledModules.length}',
                  style: GoogleFonts.poppins(
                    fontWeight: FontWeight.w900,
                    fontSize: tile.height > 1 && tile.width > 1 ? 48 : 24,
                    color: textColor,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildAppsContent(BuildContext context, WidgetRef ref, TileConfig tile) {
    final status = ref.watch(magiskStatusProvider);
    final isDarkMode = ref.watch(themeProvider);
    final colorIndex = ref.watch(tileColorProvider);
    final customColors = ref.watch(customTileColorsProvider);
    final apps = ref.watch(appsProvider);
    final localizations = AppLocalizations.of(context)!;
    
    final tileColor = AppTheme.getTileWithCustomColors(4, colorIndex, isDarkMode, customColors);
    final textColor = Colors.black87;
    
    final rootApps = apps.where((a) => a.hasRootAccess).toList();
    
    return Opacity(
      opacity: status.isRooted ? 1.0 : 0.5,
      child: Container(
        color: tileColor,
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              localizations.apps,
              style: GoogleFonts.poppins(
                fontWeight: FontWeight.w900,
                fontSize: tile.height > 1 ? 24 : 18,
                color: textColor,
              ),
            ),
            if (tile.height > 1) const SizedBox(height: 8),
            if (tile.height > 1)
              Expanded(
                child: _CarouselItemDisplay(
                  items: rootApps.map((a) => a.name).toList(),
                  startIndex: _appsCarouselIndex,
                  maxItems: tile.height > 1 ? 3 : 1,
                  textColor: textColor,
                ),
              ),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                Text(
                  '${rootApps.length}',
                  style: GoogleFonts.poppins(
                    fontWeight: FontWeight.w900,
                    fontSize: tile.height > 1 && tile.width > 1 ? 48 : 24,
                    color: textColor,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildLogsContent(BuildContext context, WidgetRef ref, TileConfig tile) {
    final filteredLogs = ref.watch(filteredLogsProvider);
    final localizations = AppLocalizations.of(context)!;
    
    final bgColor = Colors.white;
    final textColor = Colors.black87;
    
    return Container(
      color: bgColor,
      padding: const EdgeInsets.all(16.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            localizations.logs,
            style: GoogleFonts.poppins(
              fontWeight: FontWeight.w900,
              fontSize: tile.height > 1 ? 24 : 18,
              color: textColor,
            ),
          ),
          if (tile.height > 1) const SizedBox(height: 8),
          if (tile.height > 1)
            Expanded(
              child: filteredLogs.isEmpty
                  ? Center(
                      child: Text(
                        'No E/W/D logs',
                        style: GoogleFonts.poppins(
                          fontWeight: FontWeight.w500,
                          fontSize: 12,
                          color: Colors.black54,
                        ),
                      ),
                    )
                  : _LogsListView(logs: filteredLogs, textColor: textColor),
            ),
        ],
      ),
    );
  }

  Widget _buildContributorContent(BuildContext context, WidgetRef ref, TileConfig tile) {
    final status = ref.watch(magiskStatusProvider);
    final isDarkMode = ref.watch(themeProvider);
    final colorIndex = ref.watch(tileColorProvider);
    final customColors = ref.watch(customTileColorsProvider);
    final contributors = ref.watch(contributorsProvider);
    final localizations = AppLocalizations.of(context)!;
    
    final tileColor = AppTheme.getTileWithCustomColors(2, colorIndex, isDarkMode, customColors);
    final textColor = Colors.black87;
    
    return Opacity(
      opacity: status.isRooted ? 1.0 : 0.5,
      child: Container(
        color: tileColor,
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              localizations.contributors,
              style: GoogleFonts.poppins(
                fontWeight: FontWeight.w900,
                fontSize: tile.height > 1 ? 24 : 18,
                color: textColor,
              ),
            ),
            if (tile.height > 1) const SizedBox(height: 8),
            if (tile.height > 1 && contributors.isNotEmpty)
              Text(
                contributors.first.name,
                style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w500,
                  fontSize: 14,
                  color: textColor,
                ),
              ),
            if (tile.height > 1 && contributors.length > 1)
              Text(
                contributors[1].name,
                style: GoogleFonts.poppins(
                  fontWeight: FontWeight.w500,
                  fontSize: 14,
                  color: textColor,
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _buildSponsorContent(BuildContext context, WidgetRef ref, TileConfig tile) {
    final isDarkMode = ref.watch(themeProvider);
    final colorIndex = ref.watch(tileColorProvider);
    final customColors = ref.watch(customTileColorsProvider);
    final localizations = AppLocalizations.of(context)!;
    
    // Use tile ID to determine which sponsor slot (sponsor1, sponsor2, sponsor3)
    int tileIndex = 5;
    if (tile.id == 'sponsor1') tileIndex = 5;
    else if (tile.id == 'sponsor2') tileIndex = 6;
    else if (tile.id == 'sponsor3') tileIndex = 7;
    
    final tileColor = AppTheme.getTileWithCustomColors(tileIndex, colorIndex, isDarkMode, customColors);
    final textColor = Colors.black87;
    
    return Container(
      color: tileColor,
      padding: const EdgeInsets.all(16.0),
      child: Align(
        alignment: Alignment.topLeft,
        child: Text(
          localizations.sponsor ?? 'Sponsor',
          style: GoogleFonts.poppins(
            fontWeight: FontWeight.w900,
            fontSize: tile.height > 1 ? 24 : 18,
            color: textColor,
          ),
        ),
      ),
    );
  }

  Widget _buildStatusRow(String label, bool value, AppLocalizations localizations, Color textColor) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            '$label :',
            style: GoogleFonts.poppins(
              fontWeight: FontWeight.w600,
              fontSize: 14,
              color: textColor,
            ),
          ),
          Text(
            value ? localizations.yes : localizations.no,
            style: GoogleFonts.poppins(
              fontWeight: FontWeight.w900,
              fontSize: 14,
              color: textColor,
            ),
          ),
        ],
      ),
    );
  }
}

/// A widget that displays carousel items with proper animation
class _CarouselItemDisplay extends StatelessWidget {
  final List<String> items;
  final int startIndex;
  final int maxItems;
  final Color textColor;

  const _CarouselItemDisplay({
    super.key,
    required this.items,
    required this.startIndex,
    this.maxItems = 3,
    this.textColor = Colors.black,
  });

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) {
      return Center(
        child: Text(
          'Empty',
          style: GoogleFonts.poppins(
            fontWeight: FontWeight.w500,
            fontSize: 12,
            color: textColor.withOpacity(0.7),
          ),
        ),
      );
    }
    
    final displayCount = items.length > maxItems ? maxItems : items.length;
    final displayItems = <String>[];
    
    for (int i = 0; i < displayCount; i++) {
      final index = (startIndex + i) % items.length;
      displayItems.add(items[index]);
    }
    
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: displayItems.map((item) {
        return Padding(
          padding: const EdgeInsets.symmetric(vertical: 2.0),
          child: Text(
            item,
            style: GoogleFonts.poppins(
              fontWeight: FontWeight.w500,
              fontSize: 13,
              color: textColor,
              height: 1.4,
            ),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        );
      }).toList(),
    );
  }
}

class _LogsListView extends StatelessWidget {
  final List<String> logs;
  final Color textColor;

  const _LogsListView({super.key, required this.logs, this.textColor = Colors.black87});

  @override
  Widget build(BuildContext context) {
    if (logs.isEmpty) {
      return Center(
        child: Text(
          'No logs available',
          style: GoogleFonts.poppins(
            fontWeight: FontWeight.w500,
            fontSize: 12,
            color: Colors.black54,
          ),
        ),
      );
    }
    
    return ListView.builder(
      itemCount: logs.length,
      itemBuilder: (context, index) {
        final log = logs[index];
        return Padding(
          padding: const EdgeInsets.symmetric(vertical: 1.0),
          child: Text(
            log,
            style: GoogleFonts.poppins(
              fontWeight: FontWeight.w500,
              fontSize: 10,
              color: textColor,
              height: 1.3,
            ),
            overflow: TextOverflow.visible,
            maxLines: 2,
          ),
        );
      },
    );
  }
}
