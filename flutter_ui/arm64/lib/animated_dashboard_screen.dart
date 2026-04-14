import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:url_launcher/url_launcher.dart';
import 'providers/dashboard_providers.dart';
import 'navigation/flip_page_route.dart' hide AnimatedBuilder;
import 'screens/secondary_pages.dart';
import 'l10n/app_localizations.dart';
import 'utils/persistent_storage.dart';

// 爱发电赞助链接
const String _afdianSponsorUrl = 'https://afdian.com/a/SunRayEx';

/// Dashboard Screen with Metro-style UI matching the reference design
/// Features:
/// - Dynamic tile layout from tileLayoutProvider
/// - Fluent drag animation (tile follows finger, push others away)
/// - Resize with smooth transitions
/// - Tap to navigate to pages
class AnimatedDashboardScreen extends ConsumerStatefulWidget {
  const AnimatedDashboardScreen({super.key});

  @override
  ConsumerState<AnimatedDashboardScreen> createState() =>
      _AnimatedDashboardScreenState();
}

class _AnimatedDashboardScreenState
    extends ConsumerState<AnimatedDashboardScreen>
    with AutomaticKeepAliveClientMixin, TickerProviderStateMixin {
  
  // Entrance animation controller
  late final AnimationController _entranceController;
  
  // Tile position animations - maps tile.id to its animated position
  final Map<String, AnimationController> _tilePositionControllers = {};
  final Map<String, Animatable<Offset>> _tilePositionAnimations = {};
  
  // Drag state - for real-time finger tracking
  String? _draggingTileId;
  Offset? _dragStartLocalPosition; // Where finger started relative to tile
  Offset? _dragTileOriginalPosition; // Original grid position of tile
  Offset _dragCurrentOffset = Offset.zero; // Current drag offset from grid position
  
  // Target positions for other tiles (being pushed)
  Map<String, int> _targetRows = {};
  Map<String, int> _targetCols = {};
  
  // Drag optimization - prevent jitter
  int _lastDragTargetRow = -1;
  int _lastDragTargetCol = -1;
  DateTime? _lastPushTime;
  static const _pushCooldown = Duration(milliseconds: 150);
  
  // Resize state
  String? _resizingTileId;
  int? _resizeStartWidth;
  int? _resizeStartHeight;
  double? _resizeStartX;
  double? _resizeStartY;
  
  // Orientation and grid state tracking
  Orientation? _lastOrientation;
  GridConfig? _lastGridConfig;
  bool _isFirstBuild = true;
  bool _isRearrangingTiles = false;
  bool _isInitialized = false;
  
  // Current orientation and device type tracking for use in methods
  bool _currentIsLandscape = false;
  bool _currentIsTablet = false;
  
  @override
  bool get wantKeepAlive => true;

  @override
  void initState() {
    super.initState();
    
    _entranceController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 600),
    );
    
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        _entranceController.forward();
        _initTileAnimations();
        // Mark as initialized after first frame
        _isInitialized = true;
      }
    });
  }
  
  void _initTileAnimations() {
    // Initialize with both portrait and landscape configs
    final portraitConfigs = ref.read(tileLayoutPortraitProvider);
    final landscapeConfigs = ref.read(tileLayoutLandscapeProvider);
    
    // Merge both configs to ensure all tiles have animation controllers
    final allConfigs = {...portraitConfigs, ...landscapeConfigs};
    for (final tile in allConfigs) {
      _createTileAnimationController(tile.id);
    }
  }
  
  AnimationController _createTileAnimationController(String tileId) {
    if (!_tilePositionControllers.containsKey(tileId)) {
      _tilePositionControllers[tileId] = AnimationController(
        vsync: this,
        duration: const Duration(milliseconds: 200),
      );
    }
    return _tilePositionControllers[tileId]!;
  }

  @override
  void dispose() {
    _entranceController.dispose();
    for (final controller in _tilePositionControllers.values) {
      controller.dispose();
    }
    super.dispose();
  }
  
  /// Get grid configuration based on screen size, orientation and device type
  /// Phone: Portrait 3 columns x 6 rows, Landscape 6 columns x 3 rows
  /// Tablet: Portrait 6 columns x 6 rows, Landscape 8 columns x 4 rows
  GridConfig _getGridConfig(BuildContext context) {
    final screenWidth = MediaQuery.of(context).size.width;
    final screenHeight = MediaQuery.of(context).size.height;
    final orientation = MediaQuery.of(context).orientation;
    final isLandscape = orientation == Orientation.landscape;
    
    // Get the shortest side to determine device type (in dp)
    final shortestSide = screenWidth < screenHeight ? screenWidth : screenHeight;
    
    // Determine device category based on shortest side (layout-sw approach)
    // Phone: < 600dp
    // Small tablet: 600dp - 840dp
    // Large tablet: >= 840dp
    final isPhone = shortestSide < 600;
    final isSmallTablet = shortestSide >= 600 && shortestSide < 840;
    final isLargeTablet = shortestSide >= 840;
    
    // Grid configuration based on device type and orientation
    int columns;
    int rows;
    
    if (isPhone) {
      // Phone: smaller grid
      if (isLandscape) {
        // Landscape phone: 6 columns x 3 rows
        columns = 6;
        rows = 3;
      } else {
        // Portrait phone: 3 columns x 6 rows
        columns = 3;
        rows = 6;
      }
    } else {
      // Tablet: larger grid
      if (isLandscape) {
        // Landscape tablet: 8 columns x 4 rows
        columns = 8;
        rows = 4;
      } else {
        // Portrait tablet: 6 columns x 6 rows
        columns = 6;
        rows = 6;
      }
    }
    
    return GridConfig(
      columns: columns, 
      rows: rows, 
      isTablet: !isPhone, 
      isLandscape: isLandscape,
      isSmallTablet: isSmallTablet,
      isLargeTablet: isLargeTablet,
    );
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    final isDark = ref.watch(isDarkModeProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final customTileColors = ref.watch(customTileColorsProvider);
    final isLocked = ref.watch(lockModeProvider);
    
    // Determine current orientation and use appropriate layout provider
    final screenSize = MediaQuery.of(context).size;
    final isLandscape = screenSize.width > screenSize.height;
    
    // Update current orientation tracking for use in methods
    _currentIsLandscape = isLandscape;
    
    // Determine if tablet based on shortest side
    final shortestSide = screenSize.width < screenSize.height ? screenSize.width : screenSize.height;
    final isTablet = shortestSide >= 600;
    
    // Update tablet tracking for use in save methods
    _currentIsTablet = isTablet;
    
    // Watch the appropriate layout provider based on device type and orientation
    final tileConfigs;
    if (isTablet) {
      if (isLandscape) {
        tileConfigs = ref.watch(tileLayoutTabletLandscapeProvider);
      } else {
        tileConfigs = ref.watch(tileLayoutTabletPortraitProvider);
      }
    } else {
      if (isLandscape) {
        tileConfigs = ref.watch(tileLayoutLandscapeProvider);
      } else {
        tileConfigs = ref.watch(tileLayoutPortraitProvider);
      }
    }
    
    // Get adaptive grid configuration based on screen size and orientation
    final gridConfig = _getGridConfig(context);
    final screenWidth = screenSize.width;
    final orientation = MediaQuery.of(context).orientation;
    
    // Determine the correct provider for current orientation (for reference)
    // Note: We're using the tileConfigs from the correct provider already
    
    // Check for grid config change - trigger rearrangement when columns change
    // Important: Check BEFORE updating _lastGridConfig
    final needsRearrange = _lastGridConfig != null && 
        (_lastGridConfig!.columns != gridConfig.columns || 
         _lastGridConfig!.rows != gridConfig.rows);
    
    if (needsRearrange) {
      debugPrint('Grid config changed! columns: ${_lastGridConfig!.columns} -> ${gridConfig.columns}, rows: ${_lastGridConfig!.rows} -> ${gridConfig.rows}');
      // Use current _lastGridConfig as old config, then update after
      final oldGridConfig = _lastGridConfig!;
      _lastGridConfig = gridConfig;
      _lastOrientation = orientation;
      
      // Force update after build
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) {
          _handleOrientationChange(oldGridConfig, gridConfig, tileConfigs);
        }
      });
    } else {
      // No change detected, just update tracking
      _lastOrientation = orientation;
      _lastGridConfig = gridConfig;
    }
    
    // Calculate cell size based on grid configuration
    final padding = 4.0;
    final totalHorizontalPadding = padding * 2 + (gridConfig.columns - 1) * 2;
    final cellSize = (screenWidth - totalHorizontalPadding) / gridConfig.columns;
    
    // Grid height based on rows
    final totalVerticalPadding = padding * 2 + (gridConfig.rows - 1) * 2;
    final gridHeight = cellSize * gridConfig.rows + totalVerticalPadding;
    
    final backgroundColor = isDark ? AppTheme.darkBackground : AppTheme.lightBackground;
    
    // Only initialize target positions if they haven't been set yet (first build or after reset)
    // Don't reset during drag - keep the pushed positions
    if (_draggingTileId == null && _resizingTileId == null && !_isRearrangingTiles) {
      // Reset target positions to current config positions when not dragging
      for (final tile in tileConfigs) {
        _targetRows[tile.id] = tile.row;
        _targetCols[tile.id] = tile.col;
      }
    }
    
    // Reset rearrangement flag after first build
    if (_isFirstBuild) {
      _isFirstBuild = false;
    }
    
    return TickerMode(
      enabled: ModalRoute.of(context)?.isCurrent ?? true,
      child: Scaffold(
        backgroundColor: backgroundColor,
        body: SafeArea(
          child: AnimatedBuilder(
            animation: _entranceController,
            builder: (context, child) {
              return Opacity(
                opacity: _entranceController.value,
                child: child,
              );
            },
            child: Padding(
              padding: EdgeInsets.all(padding),
              child: SizedBox(
                height: gridHeight,
                child: _buildAnimatedGrid(
                  context, 
                  isDark, 
                  isLocked,
                  tileColorIndex, 
                  customTileColors, 
                  tileConfigs, 
                  cellSize,
                  gridConfig,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
  
  /// Handle orientation change by adjusting tiles to fit new grid
  /// Fixed: Now preserves user arrangements instead of resetting to defaults
  void _handleOrientationChange(GridConfig oldConfig, GridConfig newConfig, List<TileConfig> currentTiles) {
    // Skip if we're dragging or resizing
    if (_draggingTileId != null || _resizingTileId != null) {
      return;
    }
    
    // Check if grid configuration actually changed
    final columnsChanged = oldConfig.columns != newConfig.columns;
    final rowsChanged = oldConfig.rows != newConfig.rows;
    
    if (!columnsChanged && !rowsChanged) {
      debugPrint('GridConfig unchanged, skipping rearrangement');
      return; // No actual grid change
    }
    
    debugPrint('Orientation changed: ${oldConfig.columns}x${oldConfig.rows} -> ${newConfig.columns}x${newConfig.rows}');
    debugPrint('Old isLandscape: ${oldConfig.isLandscape}, New isLandscape: ${newConfig.isLandscape}');
    
    _isRearrangingTiles = true;
    
    // Use the appropriate provider based on new config (considering tablet mode)
    // Note: _currentIsTablet was updated in build() before this is called
    final List<TileConfig> freshTiles;
    if (_currentIsTablet) {
      freshTiles = _currentIsLandscape 
        ? ref.read(tileLayoutTabletLandscapeProvider)
        : ref.read(tileLayoutTabletPortraitProvider);
    } else {
      freshTiles = _currentIsLandscape 
        ? ref.read(tileLayoutLandscapeProvider)
        : ref.read(tileLayoutPortraitProvider);
    }
    
    // Adjust tiles to fit new grid bounds (only clamp, don't reset positions)
    final newTileList = freshTiles.map((tile) {
      int newRow = tile.row;
      int newCol = tile.col;
      int newWidth = tile.width;
      int newHeight = tile.height;
      
      // Clamp position and size to new grid bounds
      if (newWidth > newConfig.columns) newWidth = newConfig.columns;
      if (newHeight > newConfig.rows) newHeight = newConfig.rows;
      if (newCol >= newConfig.columns) {
        newCol = newConfig.columns - newWidth;
        if (newCol < 0) newCol = 0;
      }
      if (newRow >= newConfig.rows) {
        newRow = newConfig.rows - newHeight;
        if (newRow < 0) newRow = 0;
      }
      if (newCol + newWidth > newConfig.columns) {
        newCol = newConfig.columns - newWidth;
        if (newCol < 0) { newCol = 0; newWidth = newConfig.columns; }
      }
      if (newRow + newHeight > newConfig.rows) {
        newRow = newConfig.rows - newHeight;
        if (newRow < 0) { newRow = 0; newHeight = newConfig.rows; }
      }
      
      // Only update if bounds changed
      if (newRow != tile.row || newCol != tile.col ||
          newWidth != tile.width || newHeight != tile.height) {
        debugPrint('Adjusting tile ${tile.id}: (${tile.row}, ${tile.col}) -> (${newRow}, ${newCol}) size ${tile.width}x${tile.height} -> ${newWidth}x${newHeight}');
        return tile.copyWith(
          row: newRow,
          col: newCol,
          width: newWidth,
          height: newHeight,
        );
      }
      return tile;
    }).toList();
    
    // Update state with adjusted layout (preserving user arrangement)
    // Use the same provider we read from
    final targetProvider = _currentIsTablet
        ? (_currentIsLandscape ? tileLayoutTabletLandscapeProvider : tileLayoutTabletPortraitProvider)
        : (_currentIsLandscape ? tileLayoutLandscapeProvider : tileLayoutPortraitProvider);
    ref.read(targetProvider.notifier).state = newTileList;
    
    // Save the new layout to persistent storage
    _saveTileLayout(newTileList, newConfig.isLandscape);
    
    // Update target positions for animation - read fresh
    final tilesAfterUpdate = ref.read(targetProvider);
    for (final tile in tilesAfterUpdate) {
      _targetRows[tile.id] = tile.row;
      _targetCols[tile.id] = tile.col;
    }
    
    _isRearrangingTiles = false;
    debugPrint('Orientation change handling complete - preserved user arrangement');
  }
  
  /// Save tile layout to persistent storage (synchronous)
  /// Fixed: Now considers tablet mode when saving
  void _saveTileLayout(List<TileConfig> tiles, bool isLandscape) {
    final storage = PersistentStorage();
    final tileMaps = tiles.map((tile) => tile.toJson()).toList();
    
    // Save to the correct storage key based on device type and orientation
    if (_currentIsTablet) {
      if (isLandscape) {
        storage.saveTileLayoutTabletLandscape(tileMaps);
      } else {
        storage.saveTileLayoutTabletPortrait(tileMaps);
      }
    } else {
      if (isLandscape) {
        storage.saveTileLayoutLandscape(tileMaps);
      } else {
        storage.saveTileLayoutPortrait(tileMaps);
      }
    }
  }

  Widget _buildAnimatedGrid(
    BuildContext context,
    bool isDark,
    bool isLocked,
    int tileColorIndex,
    Map<int, Color> customTileColors,
    List<TileConfig> tileConfigs,
    double cellSize,
    GridConfig gridConfig
  ) {
    return Stack(
      clipBehavior: Clip.none,
      children: tileConfigs.map((tile) {
        // Ensure animation controller exists
        _createTileAnimationController(tile.id);
        
        // Calculate base position from target (animated position)
        final targetRow = _targetRows[tile.id] ?? tile.row;
        final targetCol = _targetCols[tile.id] ?? tile.col;
        
        // Base position (where tile should be based on target)
        final baseLeft = targetCol * cellSize + targetCol * 2;
        final baseTop = targetRow * cellSize + targetRow * 2;
        
        // If this tile is being dragged, add the drag offset
        Offset extraOffset = Offset.zero;
        if (_draggingTileId == tile.id) {
          extraOffset = _dragCurrentOffset;
        }
        
        final width = tile.width * cellSize + (tile.width - 1) * 2;
        final height = tile.height * cellSize + (tile.height - 1) * 2;
        
        return AnimatedPositioned(
          duration: _draggingTileId == tile.id 
            ? Duration.zero // Instant for dragged tile
            : const Duration(milliseconds: 200), // Smooth for others
          curve: Curves.easeOutCubic,
          left: baseLeft + extraOffset.dx,
          top: baseTop + extraOffset.dy,
          width: width,
          height: height,
          child: AnimatedScale(
            duration: const Duration(milliseconds: 150),
            scale: _draggingTileId == tile.id ? 1.05 : 1.0,
            curve: Curves.easeOut,
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 200),
              curve: Curves.easeOutCubic,
              decoration: BoxDecoration(
                boxShadow: _draggingTileId == tile.id
                  ? [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.3),
                      blurRadius: 10,
                      spreadRadius: 2,
                    )
                  ]
                  : null,
              ),
              child: _buildTile(
                context,
                tile,
                isDark,
                isLocked,
                tileColorIndex,
                customTileColors,
                cellSize,
                gridConfig,
              ),
            ),
          ),
        );
      }).toList(),
    );
  }

  Widget _buildTile(
    BuildContext context,
    TileConfig tile,
    bool isDark,
    bool isLocked,
    int tileColorIndex,
    Map<int, Color> customTileColors,
    double cellSize,
    GridConfig gridConfig,
  ) {
    // Determine tile color index based on type
    int colorTileIndex;
    switch (tile.type) {
      case 'magisk':
        colorTileIndex = 0;
        break;
      case 'settings':
        colorTileIndex = 1;
        break;
      case 'contributor':
        colorTileIndex = 2;
        break;
      case 'modules':
        colorTileIndex = 3;
        break;
      case 'apps':
        colorTileIndex = 4;
        break;
      case 'logs':
        colorTileIndex = 5;
        break;
      case 'sponsor':
        if (tile.id == 'sponsor1') colorTileIndex = 5;
        else if (tile.id == 'sponsor2') colorTileIndex = 6;
        else colorTileIndex = 7;
        break;
      default:
        colorTileIndex = 0;
    }
    
    // Logs tile color
    final Color tileColor;
    if (tile.type == 'logs') {
      tileColor = isDark ? const Color(0xFFFFFFFF) : const Color(0xFF000000);
    } else {
      tileColor = AppTheme.getTileWithCustomColors(colorTileIndex, tileColorIndex, isDark, customTileColors);
    }
    final Color textColor;
    if (tile.type == 'logs') {
      textColor = isDark ? Colors.black : Colors.white;
    } else {
      textColor = isDark ? Colors.white : Colors.black;
    }
    
    // Locked mode: tap/long press feedback
    if (isLocked) {
      return _LockedTile(
        tileColor: tileColor,
        onTap: () => _navigateToTile(context, tile),
        child: _buildTileContent(
          tile,
          isDark,
          tileColorIndex,
          customTileColors,
          cellSize,
          textColor,
          colorTileIndex,
        ),
      );
    }
    
    // Unlocked mode: drag and resize
    return GestureDetector(
      onTap: () {
        if (_draggingTileId == null && _resizingTileId == null) {
          _navigateToTile(context, tile);
        }
      },
      onLongPressStart: (details) {
        if (_resizingTileId != null) return;
        
        final screenWidth = MediaQuery.of(context).size.width;
        final cellSize = (screenWidth - 6) / 3;
        
        // Calculate tile's current grid position
        final tileLeft = tile.col * cellSize + tile.col * 2;
        final tileTop = tile.row * cellSize + tile.row * 2;
        
        setState(() {
          _draggingTileId = tile.id;
          _dragStartLocalPosition = details.localPosition;
          _dragTileOriginalPosition = Offset(tileLeft, tileTop);
          _dragCurrentOffset = Offset.zero;
        });
      },
      onLongPressMoveUpdate: (details) {
        if (_draggingTileId != tile.id) return;
        
        // Use dynamic grid config for calculations
        final gridCols = gridConfig.columns;
        final gridRows = gridConfig.rows;
        
        // Calculate drag offset from original position
        final RenderBox? renderBox = context.findRenderObject() as RenderBox?;
        if (renderBox == null) return;
        
        final gridLocalPos = renderBox.globalToLocal(details.globalPosition);
        
        // Where the tile should be based on finger
        final targetLeft = gridLocalPos.dx - _dragStartLocalPosition!.dx;
        final targetTop = gridLocalPos.dy - _dragStartLocalPosition!.dy;
        
        // Offset from original grid position
        final originalLeft = tile.col * cellSize + tile.col * 2;
        final originalTop = tile.row * cellSize + tile.row * 2;
        
        setState(() {
          _dragCurrentOffset = Offset(targetLeft - originalLeft, targetTop - originalTop);
        });
        
        // Calculate current floating position of dragged tile (including scale effect)
        // The scaled tile appears larger, so we need to account for that in overlap detection
        final floatingLeft = originalLeft + _dragCurrentOffset.dx;
        final floatingTop = originalTop + _dragCurrentOffset.dy;
        
        // Calculate target grid position using dynamic grid values
        final newCol = ((gridLocalPos.dx - 2) / (cellSize + 2)).floor().clamp(0, gridCols - tile.width).toInt();
        final newRow = ((gridLocalPos.dy - 2) / (cellSize + 2)).floor().clamp(0, gridRows - tile.height).toInt();
        
        // Check if we need to push other tiles based on floating position - use current orientation and tablet provider
        final allTiles = _currentIsTablet
            ? (_currentIsLandscape 
                ? ref.read(tileLayoutTabletLandscapeProvider)
                : ref.read(tileLayoutTabletPortraitProvider))
            : (_currentIsLandscape 
                ? ref.read(tileLayoutLandscapeProvider)
                : ref.read(tileLayoutPortraitProvider));
        _pushOtherTilesByFloatingPosition(tile, floatingLeft, floatingTop, newRow, newCol, allTiles, cellSize, gridConfig);
      },
      onLongPressEnd: (details) {
        if (_draggingTileId != tile.id) return;
        
        // Use dynamic grid config for calculations
        final gridCols = gridConfig.columns;
        final gridRows = gridConfig.rows;
        
        // Calculate final grid position
        final RenderBox? renderBox = context.findRenderObject() as RenderBox?;
        if (renderBox != null) {
          final gridLocalPos = renderBox.globalToLocal(details.globalPosition);
          final newCol = ((gridLocalPos.dx - 2) / (cellSize + 2)).floor().clamp(0, gridCols - tile.width).toInt();
          final newRow = ((gridLocalPos.dy - 2) / (cellSize + 2)).floor().clamp(0, gridRows - tile.height).toInt();
          
          // Finalize position
          _finalizeTilePosition(tile, newRow, newCol);
        }
        
        setState(() {
          _draggingTileId = null;
          _dragStartLocalPosition = null;
          _dragTileOriginalPosition = null;
          _dragCurrentOffset = Offset.zero;
        });
        
        // Save layout using correct provider - considering tablet mode
        _saveTileLayout(
          _currentIsTablet
            ? (_currentIsLandscape 
                ? ref.read(tileLayoutTabletLandscapeProvider)
                : ref.read(tileLayoutTabletPortraitProvider))
            : (_currentIsLandscape 
                ? ref.read(tileLayoutLandscapeProvider)
                : ref.read(tileLayoutPortraitProvider)),
          _currentIsLandscape
        );
      },
      child: Container(
        decoration: BoxDecoration(
          color: tileColor,
          border: Border.all(
            color: textColor.withValues(alpha: 0.3),
            width: 1,
          ),
        ),
        child: Stack(
          children: [
            _buildTileContent(
              tile,
              isDark,
              tileColorIndex,
              customTileColors,
              cellSize,
              textColor,
              colorTileIndex,
            ),
            
            // Resize handle
            if (!isLocked)
              Positioned(
                bottom: 4,
                right: 4,
                child: GestureDetector(
                  onTap: () {},
                  onPanStart: (details) {
                    setState(() {
                      _resizingTileId = tile.id;
                      _resizeStartWidth = tile.width;
                      _resizeStartHeight = tile.height;
                      _resizeStartX = details.globalPosition.dx;
                      _resizeStartY = details.globalPosition.dy;
                    });
                  },
                  onPanUpdate: (details) {
                    if (_resizingTileId != tile.id) return;
                    
                    final screenWidth = MediaQuery.of(context).size.width;
                    final cellSize = (screenWidth - 6) / 3;
                    
                    final deltaX = details.globalPosition.dx - _resizeStartX!;
                    final deltaY = details.globalPosition.dy - _resizeStartY!;
                    
                    final newWidth = (_resizeStartWidth! + (deltaX / cellSize).round()).clamp(1, 3);
                    final newHeight = (_resizeStartHeight! + (deltaY / cellSize).round()).clamp(1, 6);
                    
                    if (newWidth != tile.width || newHeight != tile.height) {
                      if (!_wouldOverlap(tile, newWidth, newHeight, gridConfig)) {
                        _resizeTileDirect(tile.id, newWidth, newHeight);
                        // Push other tiles if needed
                        _pushOtherTilesForResize(tile, newWidth, newHeight, gridConfig);
                      }
                    }
                  },
                  onPanEnd: (details) {
                    if (_resizingTileId == tile.id) {
                      setState(() {
                        _resizingTileId = null;
                      });
                      // Save layout using direct method
                      _saveCurrentLayout();
                    }
                  },
                  child: Container(
                    width: 24,
                    height: 24,
                    decoration: BoxDecoration(
                      color: tileColor.withValues(alpha: 0.8),
                      borderRadius: BorderRadius.circular(4),
                      border: Border.all(
                        color: Colors.cyanAccent,
                        width: 2,
                      ),
                    ),
                    child: Icon(
                      Icons.drag_handle,
                      size: 16,
                      color: textColor,
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
  
  /// Navigate to tile's corresponding page
  void _navigateToTile(BuildContext context, TileConfig tile) {
    final localizations = AppLocalizations.of(context)!;
    
    switch (tile.type) {
      case 'magisk':
        Navigator.push(
          context,
          FlipPageRoute(page: const MagiskManagerPage()),
        );
        break;
      case 'settings':
        Navigator.push(
          context,
          FlipPageRoute(page: const SettingsPage()),
        );
        break;
      case 'modules':
        Navigator.push(
          context,
          FlipPageRoute(page: const ModulesPage()),
        );
        break;
      case 'apps':
        Navigator.push(
          context,
          FlipPageRoute(page: const AppsPage()),
        );
        break;
      case 'logs':
        Navigator.push(
          context,
          FlipPageRoute(page: const LogsPage()),
        );
        break;
      case 'contributor':
        Navigator.push(
          context,
          FlipPageRoute(page: const ContributorsPage()),
        );
        break;
      case 'sponsor':
        // Sponsor tiles open Afdian sponsor page
        _openSponsorUrl();
        break;
    }
  }
  
  /// Open sponsor URL in browser
  Future<void> _openSponsorUrl() async {
    final uri = Uri.parse(_afdianSponsorUrl);
    try {
      if (await canLaunchUrl(uri)) {
        await launchUrl(uri, mode: LaunchMode.externalApplication);
      } else {
        debugPrint('Could not launch $_afdianSponsorUrl');
      }
    } catch (e) {
      debugPrint('Error launching URL: $e');
    }
  }
  
  /// Build tile content based on type
  Widget _buildTileContent(
    TileConfig tile,
    bool isDark,
    int tileColorIndex,
    Map<int, Color> customTileColors,
    double cellSize,
    Color textColor,
    int colorTileIndex,
  ) {
    final localizations = AppLocalizations.of(context)!;
    
    switch (tile.type) {
      case 'magisk':
        return _MagiskTileContent(
          tileWidth: tile.width,
          tileHeight: tile.height,
          cellSize: cellSize,
          textColor: textColor,
          localizations: localizations,
        );
      case 'settings':
        return _SettingsTileContent(
          cellSize: cellSize,
          textColor: textColor,
          localizations: localizations,
        );
      case 'modules':
        return _ModulesTileContent(
          tileWidth: tile.width,
          tileHeight: tile.height,
          cellSize: cellSize,
          textColor: textColor,
          localizations: localizations,
        );
      case 'apps':
        return _AppsTileContent(
          tileWidth: tile.width,
          tileHeight: tile.height,
          cellSize: cellSize,
          textColor: textColor,
          localizations: localizations,
        );
      case 'logs':
        return _LogsTileContent(
          cellSize: cellSize,
          isDark: isDark,
          textColor: textColor,
          localizations: localizations,
        );
      case 'contributor':
        return _ContributorTileContent(
          cellSize: cellSize,
          textColor: textColor,
        );
      case 'sponsor':
        return _SponsorTileContent(
          cellSize: cellSize,
          textColor: textColor,
          label: localizations.sponsor,
        );
      default:
        return Center(
          child: Text(
            tile.id,
            style: GoogleFonts.poppins(
              fontWeight: FontWeight.w900,
              fontSize: cellSize * 0.16,
              color: textColor,
            ),
          ),
        );
    }
  }
  
  /// Push other tiles based on the floating (real-time) visual position of dragged tile
  /// Optimized: Only push when dragged tile's grid position changes significantly
  /// Prevents jitter and attraction issues
  void _pushOtherTilesByFloatingPosition(
    TileConfig draggedTile,
    double floatingLeft,
    double floatingTop,
    int targetRow,
    int targetCol,
    List<TileConfig> allTiles,
    double cellSize,
    GridConfig gridConfig,
  ) {
    // Cooldown check - prevent rapid position changes
    final now = DateTime.now();
    if (_lastPushTime != null && now.difference(_lastPushTime!) < _pushCooldown) {
      return;
    }
    
    // Only push if target position changed significantly
    if (targetRow == _lastDragTargetRow && targetCol == _lastDragTargetCol) {
      return;
    }
    _lastDragTargetRow = targetRow;
    _lastDragTargetCol = targetCol;
    _lastPushTime = now;
    
    // Initialize target positions for all tiles if not already set
    for (final tile in allTiles) {
      if (!_targetRows.containsKey(tile.id)) {
        _targetRows[tile.id] = tile.row;
      }
      if (!_targetCols.containsKey(tile.id)) {
        _targetCols[tile.id] = tile.col;
      }
    }
    
    // Calculate dragged tile's grid bounds at target position
    final draggedBounds = _GridBounds(
      row: targetRow,
      col: targetCol,
      width: draggedTile.width,
      height: draggedTile.height,
    );
    
    // Find tiles that overlap with dragged tile's target position
    final overlappingTiles = <TileConfig>[];
    
    for (final otherTile in allTiles) {
      if (otherTile.id == draggedTile.id) continue;
      
      final otherTargetRow = _targetRows[otherTile.id] ?? otherTile.row;
      final otherTargetCol = _targetCols[otherTile.id] ?? otherTile.col;
      
      final otherBounds = _GridBounds(
        row: otherTargetRow,
        col: otherTargetCol,
        width: otherTile.width,
        height: otherTile.height,
      );
      
      if (_gridBoundsOverlap(draggedBounds, otherBounds)) {
        overlappingTiles.add(otherTile);
      }
    }
    
    // For each overlapping tile, find a new position
    // Use a stable algorithm: move each tile to the nearest valid position
    // in the direction away from the dragged tile
    for (final overlappingTile in overlappingTiles) {
      final currentRow = _targetRows[overlappingTile.id] ?? overlappingTile.row;
      final currentCol = _targetCols[overlappingTile.id] ?? overlappingTile.col;
      
      // Calculate push direction (away from dragged tile)
      final centerRowDiff = (currentRow + overlappingTile.height / 2) - (targetRow + draggedTile.height / 2);
      final centerColDiff = (currentCol + overlappingTile.width / 2) - (targetCol + draggedTile.width / 2);
      
      // Find new position by moving in the push direction
      int newRow = currentRow;
      int newCol = currentCol;
      
      // Try moving in primary direction first
      if (centerRowDiff.abs() > centerColDiff.abs()) {
        // Vertical push
        final pushDir = centerRowDiff > 0 ? 1 : -1;
        newRow = _findValidRow(overlappingTile, currentRow, pushDir, draggedBounds, allTiles, gridConfig);
      } else {
        // Horizontal push
        final pushDir = centerColDiff > 0 ? 1 : -1;
        newCol = _findValidCol(overlappingTile, currentCol, pushDir, draggedBounds, allTiles, gridConfig);
      }
      
      // If still invalid, find any valid position
      if (_wouldOverlapAtPosition(overlappingTile, newRow, newCol, draggedBounds, allTiles)) {
        final fallback = _findAnyValidPosition(overlappingTile, draggedBounds, allTiles, gridConfig);
        if (fallback != null) {
          newRow = fallback.row;
          newCol = fallback.col;
        }
      }
      
      if (newRow != currentRow || newCol != currentCol) {
        setState(() {
          _targetRows[overlappingTile.id] = newRow;
          _targetCols[overlappingTile.id] = newCol;
        });
      }
    }
  }
  
  /// Grid bounds helper for overlap detection
  bool _gridBoundsOverlap(_GridBounds a, _GridBounds b) {
    return !(a.row + a.height <= b.row || b.row + b.height <= a.row ||
             a.col + a.width <= b.col || b.col + b.width <= a.col);
  }
  
  /// Check if placing tile at position would overlap
  bool _wouldOverlapAtPosition(TileConfig tile, int row, int col, _GridBounds draggedBounds, List<TileConfig> allTiles) {
    final testBounds = _GridBounds(row: row, col: col, width: tile.width, height: tile.height);
    if (_gridBoundsOverlap(testBounds, draggedBounds)) return true;
    
    for (final other in allTiles) {
      if (other.id == tile.id) continue;
      final otherRow = _targetRows[other.id] ?? other.row;
      final otherCol = _targetCols[other.id] ?? other.col;
      final otherBounds = _GridBounds(row: otherRow, col: otherCol, width: other.width, height: other.height);
      if (_gridBoundsOverlap(testBounds, otherBounds)) return true;
    }
    return false;
  }
  
  /// Find valid row by moving in direction
  int _findValidRow(TileConfig tile, int startRow, int direction, _GridBounds draggedBounds, List<TileConfig> allTiles, GridConfig gridConfig) {
    final gridRows = gridConfig.rows;
    int row = startRow;
    for (int i = 0; i < gridRows; i++) {
      row += direction;
      if (row < 0 || row + tile.height > gridRows) break;
      if (!_wouldOverlapAtPosition(tile, row, _targetCols[tile.id] ?? tile.col, draggedBounds, allTiles)) {
        return row;
      }
    }
    return startRow;
  }
  
  /// Find valid col by moving in direction
  int _findValidCol(TileConfig tile, int startCol, int direction, _GridBounds draggedBounds, List<TileConfig> allTiles, GridConfig gridConfig) {
    final gridColumns = gridConfig.columns;
    int col = startCol;
    for (int i = 0; i < gridColumns; i++) {
      col += direction;
      if (col < 0 || col + tile.width > gridColumns) break;
      if (!_wouldOverlapAtPosition(tile, _targetRows[tile.id] ?? tile.row, col, draggedBounds, allTiles)) {
        return col;
      }
    }
    return startCol;
  }
  
  /// Find any valid position for tile
  _GridPosition? _findAnyValidPosition(TileConfig tile, _GridBounds draggedBounds, List<TileConfig> allTiles, GridConfig gridConfig) {
    final gridRows = gridConfig.rows;
    final gridColumns = gridConfig.columns;
    for (int r = 0; r <= gridRows - tile.height; r++) {
      for (int c = 0; c <= gridColumns - tile.width; c++) {
        if (!_wouldOverlapAtPosition(tile, r, c, draggedBounds, allTiles)) {
          return _GridPosition(row: r, col: c);
        }
      }
    }
    return null;
  }
  
  
  /// Push tiles when resizing - direct state manipulation
  void _pushOtherTilesForResize(TileConfig resizedTile, int newWidth, int newHeight, GridConfig gridConfig) {
    // Get current tiles from correct provider - considering tablet mode
    final targetProvider = _currentIsTablet
        ? (_currentIsLandscape ? tileLayoutTabletLandscapeProvider : tileLayoutTabletPortraitProvider)
        : (_currentIsLandscape ? tileLayoutLandscapeProvider : tileLayoutPortraitProvider);
    final allTiles = ref.read(targetProvider);
    final gridRows = gridConfig.rows;
    final gridColumns = gridConfig.columns;
    
    final updatedTiles = allTiles.map((otherTile) {
      if (otherTile.id == resizedTile.id) return otherTile;
      
      // Check if new size overlaps
      bool overlaps = false;
      for (int r = resizedTile.row; r < resizedTile.row + newHeight; r++) {
        for (int c = resizedTile.col; c < resizedTile.col + newWidth; c++) {
          if (r >= otherTile.row && r < otherTile.row + otherTile.height &&
              c >= otherTile.col && c < otherTile.col + otherTile.width) {
            overlaps = true;
            break;
          }
        }
        if (overlaps) break;
      }
      
      if (overlaps) {
        final pushDir = _calculatePushDirectionForResize(resizedTile, otherTile, newWidth, newHeight);
        final newRow = (otherTile.row + pushDir.dy).round().clamp(0, gridRows - otherTile.height);
        final newCol = (otherTile.col + pushDir.dx).round().clamp(0, gridColumns - otherTile.width);
        
        setState(() {
          _targetRows[otherTile.id] = newRow;
          _targetCols[otherTile.id] = newCol;
        });
        
        return otherTile.copyWith(row: newRow, col: newCol);
      }
      return otherTile;
    }).toList();
    
    // Update state directly
    ref.read(targetProvider.notifier).state = updatedTiles;
  }
  
  Offset _calculatePushDirectionForResize(TileConfig resized, TileConfig other, int newWidth, int newHeight) {
    final resizedCenterRow = resized.row + newHeight / 2;
    final resizedCenterCol = resized.col + newWidth / 2;
    final otherCenterRow = other.row + other.height / 2;
    final otherCenterCol = other.col + other.width / 2;
    
    final deltaRow = otherCenterRow - resizedCenterRow;
    final deltaCol = otherCenterCol - resizedCenterCol;
    
    int pushRow = 0;
    int pushCol = 0;
    
    // Calculate how much the tile is expanding
    int expandRow = newHeight - resized.height;
    int expandCol = newWidth - resized.width;
    
    // Push based on expansion direction
    if (expandRow > 0 && deltaRow > 0) {
      pushRow = expandRow;
    } else if (expandRow > 0 && deltaRow < 0) {
      pushRow = -expandRow;
    }
    
    if (expandCol > 0 && deltaCol > 0) {
      pushCol = expandCol;
    } else if (expandCol > 0 && deltaCol < 0) {
      pushCol = -expandCol;
    }
    
    // Fallback to relative position
    if (pushRow == 0 && pushCol == 0) {
      if (deltaRow.abs() > deltaCol.abs()) {
        pushRow = deltaRow > 0 ? 1 : -1;
      } else {
        pushCol = deltaCol > 0 ? 1 : -1;
      }
    }
    
    return Offset(pushCol.toDouble(), pushRow.toDouble());
  }
  
  /// Resize a tile directly in the state
  void _resizeTileDirect(String tileId, int newWidth, int newHeight) {
    final targetProvider = _currentIsTablet
        ? (_currentIsLandscape ? tileLayoutTabletLandscapeProvider : tileLayoutTabletPortraitProvider)
        : (_currentIsLandscape ? tileLayoutLandscapeProvider : tileLayoutPortraitProvider);
    final tiles = ref.read(targetProvider);
    final updatedTiles = tiles.map((tile) {
      if (tile.id == tileId) {
        return tile.copyWith(width: newWidth, height: newHeight);
      }
      return tile;
    }).toList();
    ref.read(targetProvider.notifier).state = updatedTiles;
  }
  
  /// Save current layout to persistent storage
  void _saveCurrentLayout() {
    List<TileConfig> tiles;
    if (_currentIsTablet) {
      tiles = _currentIsLandscape 
        ? ref.read(tileLayoutTabletLandscapeProvider)
        : ref.read(tileLayoutTabletPortraitProvider);
    } else {
      tiles = _currentIsLandscape 
        ? ref.read(tileLayoutLandscapeProvider)
        : ref.read(tileLayoutPortraitProvider);
    }
    _saveTileLayout(tiles, _currentIsLandscape);
  }
  
  /// Finalize tile position after drag ends - direct state manipulation
  void _finalizeTilePosition(TileConfig tile, int newRow, int newCol) {
    final targetProvider = _currentIsTablet
        ? (_currentIsLandscape ? tileLayoutTabletLandscapeProvider : tileLayoutTabletPortraitProvider)
        : (_currentIsLandscape ? tileLayoutLandscapeProvider : tileLayoutPortraitProvider);
    final allTiles = ref.read(targetProvider);
    
    // Build updated list - first move pushed tiles, then move dragged tile
    final updatedTiles = allTiles.map((otherTile) {
      if (otherTile.id == tile.id) {
        // This is the dragged tile - move to final position
        return otherTile.copyWith(row: newRow, col: newCol);
      }
      
      final targetRow = _targetRows[otherTile.id];
      final targetCol = _targetCols[otherTile.id];
      
      if (targetRow != null && targetCol != null && 
          (targetRow != otherTile.row || targetCol != otherTile.col)) {
        return otherTile.copyWith(row: targetRow, col: targetCol);
      }
      return otherTile;
    }).toList();
    
    // Update state directly
    ref.read(targetProvider.notifier).state = updatedTiles;
    
    // Update target positions
    setState(() {
      for (final t in updatedTiles) {
        _targetRows[t.id] = t.row;
        _targetCols[t.id] = t.col;
      }
    });
  }
  
  /// Check if resizing would overlap - direct calculation
  bool _wouldOverlap(TileConfig tile, int newWidth, int newHeight, GridConfig gridConfig) {
    final targetProvider = _currentIsTablet
        ? (_currentIsLandscape ? tileLayoutTabletLandscapeProvider : tileLayoutTabletPortraitProvider)
        : (_currentIsLandscape ? tileLayoutLandscapeProvider : tileLayoutPortraitProvider);
    final allTiles = ref.read(targetProvider);
    
    // Check bounds
    if (tile.col + newWidth > gridConfig.columns) return true;
    if (tile.row + newHeight > gridConfig.rows) return true;
    
    // Check overlap with other tiles
    for (final otherTile in allTiles) {
      if (otherTile.id == tile.id) continue;
      
      // Check if the new size overlaps with this tile
      final newLeft = tile.col;
      final newRight = tile.col + newWidth;
      final newTop = tile.row;
      final newBottom = tile.row + newHeight;
      
      final otherLeft = otherTile.col;
      final otherRight = otherTile.col + otherTile.width;
      final otherTop = otherTile.row;
      final otherBottom = otherTile.row + otherTile.height;
      
      // Check for overlap
      if (newLeft < otherRight && newRight > otherLeft &&
          newTop < otherBottom && newBottom > otherTop) {
        return true;
      }
    }
    return false;
  }
}

/// Grid configuration class for adaptive layouts
class GridConfig {
  final int columns;
  final int rows;
  final bool isTablet;
  final bool isLandscape;
  final bool isSmallTablet;
  final bool isLargeTablet;
  
  const GridConfig({
    required this.columns,
    required this.rows,
    required this.isTablet,
    required this.isLandscape,
    this.isSmallTablet = false,
    this.isLargeTablet = false,
  });
  
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is GridConfig &&
          runtimeType == other.runtimeType &&
          columns == other.columns &&
          rows == other.rows &&
          isTablet == other.isTablet &&
          isLandscape == other.isLandscape &&
          isSmallTablet == other.isSmallTablet &&
          isLargeTablet == other.isLargeTablet;

  @override
  int get hashCode => Object.hash(columns, rows, isTablet, isLandscape, isSmallTablet, isLargeTablet);
}

/// Grid bounds helper class for overlap detection
class _GridBounds {
  final int row;
  final int col;
  final int width;
  final int height;
  
  const _GridBounds({
    required this.row,
    required this.col,
    required this.width,
    required this.height,
  });
}

/// Grid position helper class
class _GridPosition {
  final int row;
  final int col;
  
  const _GridPosition({required this.row, required this.col});
}

/// Locked tile widget with tap/long press feedback
class _LockedTile extends StatefulWidget {
  final Color tileColor;
  final VoidCallback onTap;
  final Widget child;
  
  const _LockedTile({
    required this.tileColor,
    required this.onTap,
    required this.child,
  });
  
  @override
  State<_LockedTile> createState() => _LockedTileState();
}

class _LockedTileState extends State<_LockedTile> {
  bool _isPressed = false;
  bool _isLongPress = false;
  
  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown: (_) {
        setState(() {
          _isPressed = true;
          _isLongPress = false;
        });
      },
      onTapUp: (_) {
        if (!_isLongPress) {
          setState(() => _isPressed = false);
          widget.onTap();
        }
      },
      onTapCancel: () {
        setState(() {
          _isPressed = false;
          _isLongPress = false;
        });
      },
      onLongPress: () {
        // Mark as long press - when user releases, don't navigate
        setState(() {
          _isLongPress = true;
        });
      },
      onLongPressEnd: (_) {
        // Long press ended - just reset visual state, don't navigate
        setState(() {
          _isPressed = false;
        });
      },
      child: AnimatedScale(
        scale: _isPressed ? 0.92 : 1.0,
        duration: const Duration(milliseconds: 80),
        curve: Curves.easeOut,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 80),
          decoration: BoxDecoration(
            color: widget.tileColor,
            border: _isPressed 
              ? Border.all(
                  color: Colors.white.withValues(alpha: 0.3),
                  width: 2,
                )
              : null,
          ),
          child: Stack(
            children: [
              widget.child,
              // Ripple highlight effect when pressed
              if (_isPressed)
                Positioned.fill(
                  child: Container(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                        colors: [
                          Colors.white.withValues(alpha: _isLongPress ? 0.15 : 0.08),
                          Colors.white.withValues(alpha: 0.02),
                        ],
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

// ==================== Tile Content Widgets ====================

class _MagiskTileContent extends ConsumerWidget {
  final int tileWidth;
  final int tileHeight;
  final double cellSize;
  final Color textColor;
  final AppLocalizations localizations;
  
  const _MagiskTileContent({
    required this.tileWidth,
    required this.tileHeight,
    required this.cellSize,
    required this.textColor,
    required this.localizations,
  });
  
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final status = ref.watch(magiskStatusProvider);
    final appVersionAsync = ref.watch(appVersionProvider);
    
    // 1x1 tile: only show icon with left offset (15% offset) with smooth scale animation
    if (tileWidth == 1 && tileHeight == 1) {
      return Center(
        child: Container(
          margin: EdgeInsets.only(right: cellSize * 0.15),
          child: AnimatedSwitcher(
            duration: const Duration(milliseconds: 300),
            transitionBuilder: (child, animation) {
              return ScaleTransition(
                scale: animation,
                child: FadeTransition(
                  opacity: animation,
                  child: child,
                ),
              );
            },
            child: SvgPicture.asset(
              'assets/magisk_icon1.svg',
              key: ValueKey('magisk_1x1_${cellSize.hashCode}'),
              width: cellSize * 0.7125,
              height: cellSize * 0.7125,
              colorFilter: ColorFilter.mode(textColor, BlendMode.srcIn),
            ),
          ),
        ),
      );
    }
    
    // 1x2 tile: icon in top half (偏左), title and status in bottom left with smooth scale animation
    if (tileWidth == 1 && tileHeight == 2) {
      return Padding(
        padding: const EdgeInsets.all(6.0),
        child: Column(
          children: [
            // Top half: icon 偏左 (offset 15% to left using margin)
            Expanded(
              flex: 3,
              child: Center(
                child: Container(
                  margin: EdgeInsets.only(right: cellSize * 0.15),
                  child: AnimatedSwitcher(
                    duration: const Duration(milliseconds: 300),
                    transitionBuilder: (child, animation) {
                      return ScaleTransition(
                        scale: animation,
                        child: FadeTransition(
                          opacity: animation,
                          child: child,
                        ),
                      );
                    },
                    child: SvgPicture.asset(
                      'assets/magisk_icon1.svg',
                      key: ValueKey('magisk_1x2_${cellSize.hashCode}'),
                      width: cellSize * 0.7125,
                      height: cellSize * 0.7125,
                      colorFilter: ColorFilter.mode(textColor, BlendMode.srcIn),
                    ),
                  ),
                ),
              ),
            ),
            // Bottom half: title + status on left
            Expanded(
              flex: 2,
              child: Align(
                alignment: Alignment.bottomLeft,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      'MagisKube',
                      style: GoogleFonts.poppins(
                        fontWeight: FontWeight.w900,
                        fontSize: cellSize * 0.13,
                        color: textColor,
                      ),
                      overflow: TextOverflow.ellipsis,
                      maxLines: 1,
                    ),
                    Text(
                      'v${appVersionAsync.valueOrNull ?? '1.0'}',
                      style: GoogleFonts.poppins(
                        fontWeight: FontWeight.w600,
                        fontSize: cellSize * 0.09,
                        color: textColor,
                      ),
                      overflow: TextOverflow.ellipsis,
                      maxLines: 1,
                    ),
                    Text(
                      '[${status.isRooted ? localizations.enabled : localizations.disabled}]',
                      style: GoogleFonts.poppins(
                        fontWeight: FontWeight.w600,
                        fontSize: cellSize * 0.08,
                        color: textColor,
                      ),
                      overflow: TextOverflow.ellipsis,
                      maxLines: 1,
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      );
    }
    
    // 2x1 or larger: show icon + title on left, status below
    final titleFontSize = cellSize * 0.14;
    final contextFontSize = cellSize * 0.08;

    return Padding(
      padding: const EdgeInsets.all(8.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              SvgPicture.asset(
                'assets/magisk_icon1.svg',
                width: cellSize * 0.35,
                height: cellSize * 0.35,
                colorFilter: ColorFilter.mode(textColor, BlendMode.srcIn),
              ),
              const SizedBox(width: 6),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'MagisKube v${appVersionAsync.valueOrNull ?? '1.0'}',
                      style: GoogleFonts.poppins(
                        fontWeight: FontWeight.w900,
                        fontSize: titleFontSize,
                        color: textColor,
                      ),
                      overflow: TextOverflow.ellipsis,
                      maxLines: 1,
                    ),
                    Text(
                      '[${status.isRooted ? localizations.enabled : localizations.disabled}]',
                      style: GoogleFonts.poppins(
                        fontWeight: FontWeight.w600,
                        fontSize: titleFontSize * 0.7,
                        color: textColor,
                      ),
                      overflow: TextOverflow.ellipsis,
                      maxLines: 1,
                    ),
                  ],
                ),
              ),
            ],
          ),
          if (tileWidth >= 2 && tileHeight >= 2) ...[
            const Spacer(),
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildStatusRow(localizations.root, status.isRooted, contextFontSize, textColor),
                _buildStatusRow(localizations.zygisk, status.isZygiskEnabled, contextFontSize, textColor),
                _buildStatusRow(localizations.ramdisk, status.isRamdiskLoaded, contextFontSize, textColor),
              ],
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildStatusRow(String label, bool value, double fontSize, Color textColor) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 1.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text('$label :', style: GoogleFonts.poppins(fontWeight: FontWeight.w600, fontSize: fontSize, color: textColor)),
          Text(value ? 'Yes' : 'No', style: GoogleFonts.poppins(fontWeight: FontWeight.w900, fontSize: fontSize, color: textColor)),
        ],
      ),
    );
  }
}

class _SettingsTileContent extends ConsumerWidget {
  final double cellSize;
  final Color textColor;
  final AppLocalizations localizations;
  
  const _SettingsTileContent({
    required this.cellSize,
    required this.textColor,
    required this.localizations,
  });
  
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final status = ref.watch(magiskStatusProvider);
    final isClickable = status.isRooted;
    final titleSize = cellSize * 0.16;

    return Padding(
      padding: const EdgeInsets.all(8.0),
      child: Opacity(
        opacity: isClickable ? 1.0 : 0.5,
        child: Align(
          alignment: Alignment.topLeft,
          child: Text(
            localizations.settings,
            style: GoogleFonts.poppins(
              fontWeight: FontWeight.w900,
              fontSize: titleSize,
              color: textColor,
            ),
          ),
        ),
      ),
    );
  }
}

class _ContributorTileContent extends ConsumerWidget {
  final double cellSize;
  final Color textColor;
  
  const _ContributorTileContent({
    required this.cellSize,
    required this.textColor,
  });
  
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final contributors = ref.watch(contributorsProvider);
    final localizations = AppLocalizations.of(context)!;
    final titleSize = cellSize * 0.16;
    final textSize = cellSize * 0.09;

    return Padding(
      padding: const EdgeInsets.all(8.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            localizations.contributors,
            style: GoogleFonts.poppins(
              fontWeight: FontWeight.w900,
              fontSize: titleSize,
              color: textColor,
            ),
          ),
          const SizedBox(height: 4),
          Expanded(
            child: _ScrollingTextCarousel(
              items: contributors.map((c) => c.name).toList(),
              visibleItems: 2,
              infinite: true,
              duration: const Duration(seconds: 2),
              textStyle: GoogleFonts.poppins(
                fontWeight: FontWeight.w500,
                fontSize: textSize,
                color: textColor,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ModulesTileContent extends ConsumerWidget {
  final int tileWidth;
  final int tileHeight;
  final double cellSize;
  final Color textColor;
  final AppLocalizations localizations;
  
  const _ModulesTileContent({
    required this.tileWidth,
    required this.tileHeight,
    required this.cellSize,
    required this.textColor,
    required this.localizations,
  });
  
  int _getVisibleItems() {
    // 1x2: 5 items, 1x3: 8 items, 1x4: 10 items
    if (tileHeight >= 4) return 10;
    if (tileHeight >= 3) return 8;
    if (tileHeight >= 2) return 5;
    return 1;
  }
  
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final modules = ref.watch(modulesProvider);
    final enabledModules = modules.where((m) => m.isEnabled).toList();
    final titleSize = cellSize * 0.16;
    final textSize = cellSize * 0.1;
    final countSize = cellSize * 0.25;
    final visibleItems = _getVisibleItems();

    return Padding(
      padding: const EdgeInsets.all(6.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            localizations.modules,
            style: GoogleFonts.poppins(fontWeight: FontWeight.w900, fontSize: titleSize, color: textColor),
          ),
          const SizedBox(height: 4),
          Expanded(
            child: _ScrollingTextCarousel(
              items: enabledModules.map((m) => m.name).toList(),
              visibleItems: visibleItems,
              infinite: visibleItems > 1,
              duration: const Duration(seconds: 3),
              textStyle: GoogleFonts.poppins(fontWeight: FontWeight.w500, fontSize: textSize, color: textColor),
            ),
          ),
          Align(
            alignment: Alignment.bottomRight,
            child: Text(
              '${enabledModules.length}',
              style: GoogleFonts.poppins(fontWeight: FontWeight.w900, fontSize: countSize, color: textColor),
            ),
          ),
        ],
      ),
    );
  }
}

class _AppsTileContent extends ConsumerWidget {
  final int tileWidth;
  final int tileHeight;
  final double cellSize;
  final Color textColor;
  final AppLocalizations localizations;
  
  const _AppsTileContent({
    required this.tileWidth,
    required this.tileHeight,
    required this.cellSize,
    required this.textColor,
    required this.localizations,
  });
  
  int _getVisibleItems() {
    // 1x2: 5 items, 1x3: 8 items, 1x4: 10 items
    if (tileHeight >= 4) return 10;
    if (tileHeight >= 3) return 8;
    if (tileHeight >= 2) return 5;
    return 1;
  }
  
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final apps = ref.watch(appsProvider);
    final rootApps = apps.where((a) => a.hasRootAccess).toList();
    final titleSize = cellSize * 0.16;
    final textSize = cellSize * 0.1;
    final countSize = cellSize * 0.25;
    final visibleItems = _getVisibleItems();

    return Padding(
      padding: const EdgeInsets.all(6.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            localizations.apps,
            style: GoogleFonts.poppins(fontWeight: FontWeight.w900, fontSize: titleSize, color: textColor),
          ),
          const SizedBox(height: 4),
          Expanded(
            child: _ScrollingTextCarousel(
              items: rootApps.map((a) => a.name).toList(),
              visibleItems: visibleItems,
              infinite: visibleItems > 1,
              duration: const Duration(seconds: 3),
              textStyle: GoogleFonts.poppins(fontWeight: FontWeight.w500, fontSize: textSize, color: textColor),
            ),
          ),
          Align(
            alignment: Alignment.bottomRight,
            child: Text(
              '${rootApps.length}',
              style: GoogleFonts.poppins(fontWeight: FontWeight.w900, fontSize: countSize, color: textColor),
            ),
          ),
        ],
      ),
    );
  }
}

class _LogsTileContent extends ConsumerWidget {
  final double cellSize;
  final bool isDark;
  final Color textColor;
  final AppLocalizations localizations;
  
  const _LogsTileContent({
    required this.cellSize,
    required this.isDark,
    required this.textColor,
    required this.localizations,
  });
  
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final logsAsync = ref.watch(logsProvider);
    final titleSize = cellSize * 0.16;
    final textSize = cellSize * 0.08;

    return Padding(
      padding: const EdgeInsets.all(8.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            localizations.logs,
            style: GoogleFonts.poppins(fontWeight: FontWeight.w900, fontSize: titleSize, color: textColor),
          ),
          const SizedBox(height: 4),
          Expanded(
            child: logsAsync.when(
              data: (logs) {
                final ewLogs = logs.where((log) => log.contains(' E :') || log.contains(' W :')).take(20).toList();
                if (ewLogs.isEmpty) {
                  return Center(
                    child: Text('No E/W logs', style: GoogleFonts.poppins(fontWeight: FontWeight.w500, fontSize: textSize, color: textColor)),
                  );
                }
                return ListView.builder(
                  itemCount: ewLogs.length,
                  itemBuilder: (context, index) {
                    return Padding(
                      padding: const EdgeInsets.only(bottom: 2.0),
                      child: Text(
                        ewLogs[index].trim(),
                        style: GoogleFonts.poppins(fontWeight: FontWeight.w500, fontSize: textSize, color: textColor),
                        overflow: TextOverflow.ellipsis,
                        maxLines: 1,
                      ),
                    );
                  },
                );
              },
              loading: () => Center(child: CircularProgressIndicator(color: textColor)),
              error: (error, stack) => Text('[E]', style: GoogleFonts.poppins(fontWeight: FontWeight.w900, fontSize: textSize * 1.5, color: textColor)),
            ),
          ),
        ],
      ),
    );
  }
}

class _SponsorTileContent extends StatelessWidget {
  final double cellSize;
  final Color textColor;
  final String label;
  
  const _SponsorTileContent({
    required this.cellSize,
    required this.textColor,
    required this.label,
  });
  
  @override
  Widget build(BuildContext context) {
    final fontSize = cellSize * 0.16;
    
    return Padding(
      padding: const EdgeInsets.all(8.0),
      child: Align(
        alignment: Alignment.topLeft,
        child: Text(
          label,
          style: GoogleFonts.poppins(
            fontWeight: FontWeight.w900,
            fontSize: fontSize,
            color: textColor,
          ),
        ),
      ),
    );
  }
}

class _ScrollingTextCarousel extends StatefulWidget {
  final List<String> items;
  final int visibleItems;
  final bool infinite;
  final Duration duration;
  final TextStyle? textStyle;

  const _ScrollingTextCarousel({
    required this.items,
    this.visibleItems = 1,
    this.infinite = true,
    this.duration = const Duration(seconds: 2),
    this.textStyle,
  });

  @override
  State<_ScrollingTextCarousel> createState() => _ScrollingTextCarouselState();
}

class _ScrollingTextCarouselState extends State<_ScrollingTextCarousel> with SingleTickerProviderStateMixin {
  int _startIndex = 0;
  late AnimationController _controller;
  late Animation<double> _slideAnimation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 400),
    );
    _slideAnimation = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeInOut),
    );
    _startAnimation();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _startAnimation() {
    if (widget.items.length <= widget.visibleItems) return;
    
    Future.delayed(widget.duration, () {
      if (!mounted) return;
      _animateNext();
    });
  }

  void _animateNext() {
    if (!mounted || widget.items.length <= widget.visibleItems) return;
    
    _controller.forward().then((_) {
      if (!mounted) return;
      setState(() {
        _startIndex = (_startIndex + 1) % widget.items.length;
      });
      _controller.reset();
      _startAnimation();
    });
  }

  @override
  Widget build(BuildContext context) {
    final fontSize = widget.textStyle?.fontSize ?? 14.0;
    final itemHeight = fontSize + 6.0;
    
    if (widget.items.isEmpty) {
      return SizedBox(
        height: itemHeight * widget.visibleItems,
        child: Center(child: Text('No data', style: widget.textStyle)),
      );
    }
    
    if (widget.items.length <= widget.visibleItems) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: widget.items.take(widget.visibleItems).map((item) => SizedBox(
          height: itemHeight,
          child: Text(item, style: widget.textStyle, overflow: TextOverflow.ellipsis, maxLines: 1),
        )).toList(),
      );
    }

    final containerHeight = itemHeight * widget.visibleItems;
    
    final displayItems = <String>[];
    for (int i = 0; i < widget.visibleItems + 1; i++) {
      displayItems.add(widget.items[(_startIndex + i) % widget.items.length]);
    }

    return ClipRect(
      child: SizedBox(
        height: containerHeight,
        child: AnimatedBuilder(
          animation: _slideAnimation,
          builder: (context, child) {
            final offset = -_slideAnimation.value * itemHeight;
            return Transform.translate(
              offset: Offset(0, offset),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: displayItems.map((item) => SizedBox(
                  height: itemHeight,
                  child: Text(item, style: widget.textStyle, overflow: TextOverflow.ellipsis, maxLines: 1),
                )).toList(),
              ),
            );
          },
        ),
      ),
    );
  }
}
