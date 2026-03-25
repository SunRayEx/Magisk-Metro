import 'dart:math' as math;
import 'package:flutter/material.dart';

/**
 * 3D 翻转页面路由动画
 * 
 * 动画原理：
 * 1. 使用 Matrix4.identity()..setEntry(3, 2, 0.001) 创建透视矩阵
 *    - setEntry(3, 2, 0.001) 设置透视深度，值越大透视越明显
 *    - rotateY(angle) 绕 Y 轴旋转实现 3D 翻转效果
 * 
 * 2. 使用 Curves.fastOutSlowIn 实现非线性动画
 *    - 开始和结束时速度较慢，中间速度快
 *    - 模拟真实世界的物理运动感
 * 
 * 3. 页面过渡：
 *    - 新页面从右侧翻开进入，带着 3D 翻转效果
 */
class FlipPageRoute<T> extends PageRouteBuilder<T> {
  final Widget page;

  FlipPageRoute({required this.page})
      : super(
          pageBuilder: (context, animation, secondaryAnimation) => page,
          transitionDuration: const Duration(milliseconds: 400),
          reverseTransitionDuration: const Duration(milliseconds: 400),
          transitionsBuilder: (context, animation, secondaryAnimation, child) {
            // 使用 fastOutSlowIn 曲线实现非线性动画
            final curvedAnimation = CurvedAnimation(
              parent: animation,
              curve: Curves.fastOutSlowIn,
              reverseCurve: Curves.fastOutSlowIn,
            );

            return AnimatedBuilder(
              animation: curvedAnimation,
              child: child, // ← 这里改了: 将子组件作为 child 传入，避免动画过程中的重建
              builder: (context, cachedChild) {
                final progress = curvedAnimation.value;

                // ==================== Matrix4 3D 翻转核心实现 ====================
                // 页面从 180度（背面）翻转到 0度（正面）
                final double angle = (1.0 - progress) * math.pi;

                // 创建透视矩阵 - 从右侧翻开
                final Matrix4 transform = Matrix4.identity()
                  ..setEntry(3, 2, 0.001) // 透视效果
                  ..rotateY(-angle); // 从右侧翻开

                return Transform(
                  transform: transform,
                  alignment: Alignment.centerRight, // 从右侧翻开
                  child: cachedChild, // ← 这里改了: 直接使用缓存的 cachedChild
                );
              },
            );
          },
        );
}

// 自定义 AnimatedBuilder 组件
class AnimatedBuilder extends AnimatedWidget {
  final Widget Function(BuildContext context, Widget? child) builder;
  final Widget? child;

  const AnimatedBuilder({
    super.key,
    required Animation<double> animation,
    required this.builder,
    this.child,
  }) : super(listenable: animation);

  @override
  Widget build(BuildContext context) {
    return builder(context, child);
  }
}
