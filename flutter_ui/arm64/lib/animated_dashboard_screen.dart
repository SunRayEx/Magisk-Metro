import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'providers/dashboard_providers.dart';
import 'navigation/flip_page_route.dart' hide AnimatedBuilder;
import 'screens/secondary_pages.dart';
import 'l10n/app_localizations.dart';

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
      }
    });
  }
  
  void _initTileAnimations() {
    final tileConfigs = ref.read(tileLayoutProvider);
    for (final tile in tileConfigs) {
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
  
  @override
  Widget build(BuildContext context) {
    super.build(context);
    final isDark = ref.watch(isDarkModeProvider);
    final tileColorIndex = ref.watch(tileColorProvider);
    final customTileColors = ref.watch(customTileColorsProvider);
    final tileConfigs = ref.watch(tileLayoutProvider);
    final isLocked = ref.watch(lockModeProvider);
    final screenWidth = MediaQuery.of(context).size.width;
    
    // Grid: 3 columns x 6 rows, each cell is square
    final cellSize = (screenWidth - 6) / 3;
    // Grid height = 6 rows
    final gridHeight = cellSize * 6 + 10; // 6 rows + padding
    
    final backgroundColor = isDark ? AppTheme.darkBackground : AppTheme.lightBackground;
    
    // Only initialize target positions if they haven't been set yet (first build or after reset)
    // Don't reset during drag - keep the pushed positions
    if (_draggingTileId == null && _resizingTileId == null) {
      // Reset target positions to current config positions when not dragging
      for (final tile in tileConfigs) {
        _targetRows[tile.id] = tile.row;
        _targetCols[tile.id] = tile.col;
      }
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
              padding: const EdgeInsets.all(2.0),
              child: SizedBox(
                height: gridHeight,
                child: _buildAnimatedGrid(
                  context, 
                  isDark, 
                  isLocked,
                  tileColorIndex, 
                  customTileColors, 
                  tileConfigs, 
                  cellSize
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildAnimatedGrid(
    BuildContext context,
    bool isDark,
    bool isLocked,
    int tileColorIndex,
    Map<int, Color> customTileColors,
    List<TileConfig> tileConfigs,
    double cellSize
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
        
        final screenWidth = MediaQuery.of(context).size.width;
        final cellSize = (screenWidth - 6) / 3;
        
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
        
        // Calculate target grid position
        final newCol = ((gridLocalPos.dx - 2) / (cellSize + 2)).floor().clamp(0, TileConfig.gridColumns - tile.width).toInt();
        final newRow = ((gridLocalPos.dy - 2) / (cellSize + 2)).floor().clamp(0, TileConfig.gridRows - tile.height).toInt();
        
        // Check if we need to push other tiles based on floating position
        final allTiles = ref.read(tileLayoutProvider);
        _pushOtherTilesByFloatingPosition(tile, floatingLeft, floatingTop, newRow, newCol, allTiles, cellSize);
      },
      onLongPressEnd: (details) {
        if (_draggingTileId != tile.id) return;
        
        final screenWidth = MediaQuery.of(context).size.width;
        final cellSize = (screenWidth - 6) / 3;
        
        // Calculate final grid position
        final RenderBox? renderBox = context.findRenderObject() as RenderBox?;
        if (renderBox != null) {
          final gridLocalPos = renderBox.globalToLocal(details.globalPosition);
          final newCol = ((gridLocalPos.dx - 2) / (cellSize + 2)).floor().clamp(0, TileConfig.gridColumns - tile.width).toInt();
          final newRow = ((gridLocalPos.dy - 2) / (cellSize + 2)).floor().clamp(0, TileConfig.gridRows - tile.height).toInt();
          
          // Finalize position
          _finalizeTilePosition(tile, newRow, newCol);
        }
        
        setState(() {
          _draggingTileId = null;
          _dragStartLocalPosition = null;
          _dragTileOriginalPosition = null;
          _dragCurrentOffset = Offset.zero;
        });
        
        // Save layout
        ref.read(tileLayoutProvider.notifier).saveLayout();
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
                      if (!_wouldOverlap(tile, newWidth, newHeight)) {
                        ref.read(tileLayoutProvider.notifier).resizeTile(tile.id, newWidth, newHeight);
                        // Push other tiles if needed
                        _pushOtherTilesForResize(tile, newWidth, newHeight);
                      }
                    }
                  },
                  onPanEnd: (details) {
                    if (_resizingTileId == tile.id) {
                      setState(() {
                        _resizingTileId = null;
                      });
                      ref.read(tileLayoutProvider.notifier).saveLayout();
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
        // Sponsor tiles show a simple info dialog
        showDialog(
          context: context,
          builder: (context) => AlertDialog(
            title: Text(localizations.sponsor),
            content: Text(localizations.sponsorInfo),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: Text(localizations.ok),
              ),
            ],
          ),
        );
        break;
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
          cellSize: cellSize,
          textColor: textColor,
          localizations: localizations,
        );
      case 'apps':
        return _AppsTileContent(
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
        newRow = _findValidRow(overlappingTile, currentRow, pushDir, draggedBounds, allTiles);
      } else {
        // Horizontal push
        final pushDir = centerColDiff > 0 ? 1 : -1;
        newCol = _findValidCol(overlappingTile, currentCol, pushDir, draggedBounds, allTiles);
      }
      
      // If still invalid, find any valid position
      if (_wouldOverlapAtPosition(overlappingTile, newRow, newCol, draggedBounds, allTiles)) {
        final fallback = _findAnyValidPosition(overlappingTile, draggedBounds, allTiles);
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
  int _findValidRow(TileConfig tile, int startRow, int direction, _GridBounds draggedBounds, List<TileConfig> allTiles) {
    int row = startRow;
    for (int i = 0; i < TileConfig.gridRows; i++) {
      row += direction;
      if (row < 0 || row + tile.height > TileConfig.gridRows) break;
      if (!_wouldOverlapAtPosition(tile, row, _targetCols[tile.id] ?? tile.col, draggedBounds, allTiles)) {
        return row;
      }
    }
    return startRow;
  }
  
  /// Find valid col by moving in direction
  int _findValidCol(TileConfig tile, int startCol, int direction, _GridBounds draggedBounds, List<TileConfig> allTiles) {
    int col = startCol;
    for (int i = 0; i < TileConfig.gridColumns; i++) {
      col += direction;
      if (col < 0 || col + tile.width > TileConfig.gridColumns) break;
      if (!_wouldOverlapAtPosition(tile, _targetRows[tile.id] ?? tile.row, col, draggedBounds, allTiles)) {
        return col;
      }
    }
    return startCol;
  }
  
  /// Find any valid position for tile
  _GridPosition? _findAnyValidPosition(TileConfig tile, _GridBounds draggedBounds, List<TileConfig> allTiles) {
    for (int r = 0; r <= TileConfig.gridRows - tile.height; r++) {
      for (int c = 0; c <= TileConfig.gridColumns - tile.width; c++) {
        if (!_wouldOverlapAtPosition(tile, r, c, draggedBounds, allTiles)) {
          return _GridPosition(row: r, col: c);
        }
      }
    }
    return null;
  }
  
  
  /// Push tiles when resizing
  void _pushOtherTilesForResize(TileConfig resizedTile, int newWidth, int newHeight) {
    final allTiles = ref.read(tileLayoutProvider);
    
    for (final otherTile in allTiles) {
      if (otherTile.id == resizedTile.id) continue;
      
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
        final newRow = (otherTile.row + pushDir.dy).round().clamp(0, TileConfig.gridRows - otherTile.height);
        final newCol = (otherTile.col + pushDir.dx).round().clamp(0, TileConfig.gridColumns - otherTile.width);
        
        setState(() {
          _targetRows[otherTile.id] = newRow;
          _targetCols[otherTile.id] = newCol;
        });
        
        // Also update in provider
        ref.read(tileLayoutProvider.notifier).moveTile(otherTile.id, newRow, newCol);
      }
    }
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
  
  /// Finalize tile position after drag ends
  void _finalizeTilePosition(TileConfig tile, int newRow, int newCol) {
    final layoutNotifier = ref.read(tileLayoutProvider.notifier);
    final allTiles = ref.read(tileLayoutProvider);
    
    // First, move all pushed tiles to their target positions
    for (final otherTile in allTiles) {
      if (otherTile.id == tile.id) continue;
      
      final targetRow = _targetRows[otherTile.id] ?? otherTile.row;
      final targetCol = _targetCols[otherTile.id] ?? otherTile.col;
      
      if (targetRow != otherTile.row || targetCol != otherTile.col) {
        layoutNotifier.moveTile(otherTile.id, targetRow, targetCol);
      }
    }
    
    // Then move the dragged tile to its final position
    layoutNotifier.moveTile(tile.id, newRow, newCol);
    
    // Update all target positions to match final positions
    final updatedTiles = ref.read(tileLayoutProvider);
    setState(() {
      for (final t in updatedTiles) {
        _targetRows[t.id] = t.row;
        _targetCols[t.id] = t.col;
      }
    });
  }
  
  /// Check if resizing would overlap
  bool _wouldOverlap(TileConfig tile, int newWidth, int newHeight) {
    final layoutNotifier = ref.read(tileLayoutProvider.notifier);
    
    for (int r = tile.row; r < tile.row + newHeight; r++) {
      for (int c = tile.col; c < tile.col + newWidth; c++) {
        if (c >= TileConfig.gridColumns) return true;
        if (layoutNotifier.isPositionOccupied(r, c, tile.id)) {
          return true;
        }
      }
    }
    return false;
  }
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
    final isSmallTile = tileWidth == 1;
    final titleFontSize = isSmallTile ? cellSize * 0.12 : cellSize * 0.14;
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
                      isSmallTile ? 'Magisk' : 'Magisk ${status.versionCode}',
                      style: GoogleFonts.poppins(
                        fontWeight: FontWeight.w900,
                        fontSize: titleFontSize,
                        color: textColor,
                      ),
                      overflow: TextOverflow.ellipsis,
                      maxLines: 1,
                    ),
                    if (!isSmallTile || tileHeight >= 2)
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
  final double cellSize;
  final Color textColor;
  final AppLocalizations localizations;
  
  const _ModulesTileContent({
    required this.cellSize,
    required this.textColor,
    required this.localizations,
  });
  
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final modules = ref.watch(modulesProvider);
    final enabledModules = modules.where((m) => m.isEnabled).toList();
    final titleSize = cellSize * 0.16;
    final textSize = cellSize * 0.1;
    final countSize = cellSize * 0.25;

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
              visibleItems: 1,
              infinite: true,
              duration: const Duration(seconds: 2),
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
  final double cellSize;
  final Color textColor;
  final AppLocalizations localizations;
  
  const _AppsTileContent({
    required this.cellSize,
    required this.textColor,
    required this.localizations,
  });
  
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final apps = ref.watch(appsProvider);
    final rootApps = apps.where((a) => a.hasRootAccess).toList();
    final titleSize = cellSize * 0.16;
    final textSize = cellSize * 0.1;
    final countSize = cellSize * 0.25;

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
              visibleItems: 1,
              infinite: true,
              duration: const Duration(seconds: 2),
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
