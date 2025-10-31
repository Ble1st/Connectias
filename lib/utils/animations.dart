/// Animations and Transitions – UX Verbesserungen
///
/// Zentrale Animations-Utilities für bessere Benutzererfahrung
library;

import 'package:flutter/material.dart';

/// Animations Utility Class
class ConnectiasAnimations {
  // =========================================================================
  // PAGE TRANSITIONS
  // =========================================================================

  /// Slide transition from right
  static Route<T> slideFromRight<T>(Widget page) {
    return PageRouteBuilder<T>(
      pageBuilder: (context, animation, secondaryAnimation) => page,
      transitionsBuilder: (context, animation, secondaryAnimation, child) {
        const begin = Offset(1.0, 0.0);
        const end = Offset.zero;
        const curve = Curves.easeInOut;

        var tween = Tween(
          begin: begin,
          end: end,
        ).chain(CurveTween(curve: curve));

        return SlideTransition(position: animation.drive(tween), child: child);
      },
    );
  }

  /// Slide transition from bottom
  static Route<T> slideFromBottom<T>(Widget page) {
    return PageRouteBuilder<T>(
      pageBuilder: (context, animation, secondaryAnimation) => page,
      transitionsBuilder: (context, animation, secondaryAnimation, child) {
        const begin = Offset(0.0, 1.0);
        const end = Offset.zero;
        const curve = Curves.easeInOut;

        var tween = Tween(
          begin: begin,
          end: end,
        ).chain(CurveTween(curve: curve));

        return SlideTransition(position: animation.drive(tween), child: child);
      },
    );
  }

  /// Fade transition
  static Route<T> fadeTransition<T>(Widget page) {
    return PageRouteBuilder<T>(
      pageBuilder: (context, animation, secondaryAnimation) => page,
      transitionsBuilder: (context, animation, secondaryAnimation, child) {
        return FadeTransition(opacity: animation, child: child);
      },
    );
  }

  /// Scale transition
  static Route<T> scaleTransition<T>(Widget page) {
    return PageRouteBuilder<T>(
      pageBuilder: (context, animation, secondaryAnimation) => page,
      transitionsBuilder: (context, animation, secondaryAnimation, child) {
        return ScaleTransition(scale: animation, child: child);
      },
    );
  }

  // =========================================================================
  // WIDGET ANIMATIONS
  // =========================================================================

  /// Animated container with size and color changes
  static Widget animatedContainer({
    required Widget child,
    required Duration duration,
    Curve curve = Curves.easeInOut,
    double? width,
    double? height,
    Color? color,
    EdgeInsetsGeometry? padding,
    EdgeInsetsGeometry? margin,
    BorderRadius? borderRadius,
  }) {
    return AnimatedContainer(
      duration: duration,
      curve: curve,
      width: width,
      height: height,
      color: color,
      padding: padding,
      margin: margin,
      decoration: BoxDecoration(borderRadius: borderRadius),
      child: child,
    );
  }

  /// Animated opacity
  static Widget animatedOpacity({
    required Widget child,
    required bool visible,
    Duration duration = const Duration(milliseconds: 300),
    Curve curve = Curves.easeInOut,
  }) {
    return AnimatedOpacity(
      opacity: visible ? 1.0 : 0.0,
      duration: duration,
      curve: curve,
      child: child,
    );
  }

  /// Animated rotation
  static Widget animatedRotation({
    required Widget child,
    required double turns,
    Duration duration = const Duration(milliseconds: 300),
    Curve curve = Curves.easeInOut,
  }) {
    return AnimatedRotation(
      turns: turns,
      duration: duration,
      curve: curve,
      child: child,
    );
  }

  /// Animated scale
  static Widget animatedScale({
    required Widget child,
    required double scale,
    Duration duration = const Duration(milliseconds: 300),
    Curve curve = Curves.easeInOut,
  }) {
    return AnimatedScale(
      scale: scale,
      duration: duration,
      curve: curve,
      child: child,
    );
  }

  // =========================================================================
  // LOADING ANIMATIONS
  // =========================================================================

  /// Pulsing animation
  static Widget pulsing({
    required Widget child,
    Duration duration = const Duration(milliseconds: 1000),
    double minScale = 0.8,
    double maxScale = 1.0,
  }) {
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: minScale, end: maxScale),
      duration: duration,
      builder: (context, value, child) {
        return Transform.scale(scale: value, child: child);
      },
      child: child,
    );
  }

  /// Rotating animation
  static Widget rotating({
    required Widget child,
    Duration duration = const Duration(seconds: 2),
  }) {
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0.0, end: 1.0),
      duration: duration,
      builder: (context, value, child) {
        return Transform.rotate(angle: value * 2 * 3.14159, child: child);
      },
      child: child,
    );
  }

  /// Bouncing animation
  static Widget bouncing({
    required Widget child,
    Duration duration = const Duration(milliseconds: 600),
    double bounceHeight = 10.0,
  }) {
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0.0, end: 1.0),
      duration: duration,
      builder: (context, value, child) {
        final bounce = (value * 2 - 1).abs();
        final offset = bounce * bounceHeight;
        return Transform.translate(offset: Offset(0, -offset), child: child);
      },
      child: child,
    );
  }

  // =========================================================================
  // LIST ANIMATIONS
  // =========================================================================

  /// Staggered list animation
  static Widget staggeredList({
    required List<Widget> children,
    Duration staggerDelay = const Duration(milliseconds: 100),
    Duration animationDuration = const Duration(milliseconds: 300),
    Curve curve = Curves.easeInOut,
  }) {
    return Column(
      children: children.asMap().entries.map((entry) {
        final index = entry.key;
        final child = entry.value;

        return TweenAnimationBuilder<double>(
          tween: Tween(begin: 0.0, end: 1.0),
          duration: Duration(
            milliseconds:
                animationDuration.inMilliseconds +
                (index * staggerDelay.inMilliseconds),
          ),
          curve: curve,
          builder: (context, value, child) {
            return Transform.translate(
              offset: Offset(0, 50 * (1 - value)),
              child: Opacity(opacity: value, child: child),
            );
          },
          child: child,
        );
      }).toList(),
    );
  }

  /// Slide in from left animation
  static Widget slideInFromLeft({
    required Widget child,
    Duration duration = const Duration(milliseconds: 300),
    Curve curve = Curves.easeInOut,
    double offset = 100.0,
  }) {
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0.0, end: 1.0),
      duration: duration,
      curve: curve,
      builder: (context, value, child) {
        return Transform.translate(
          offset: Offset(-offset * (1 - value), 0),
          child: Opacity(opacity: value, child: child),
        );
      },
      child: child,
    );
  }

  /// Slide in from right animation
  static Widget slideInFromRight({
    required Widget child,
    Duration duration = const Duration(milliseconds: 300),
    Curve curve = Curves.easeInOut,
    double offset = 100.0,
  }) {
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0.0, end: 1.0),
      duration: duration,
      curve: curve,
      builder: (context, value, child) {
        return Transform.translate(
          offset: Offset(offset * (1 - value), 0),
          child: Opacity(opacity: value, child: child),
        );
      },
      child: child,
    );
  }

  // =========================================================================
  // CARD ANIMATIONS
  // =========================================================================

  /// Animated card with hover effect
  static Widget animatedCard({
    required Widget child,
    Duration duration = const Duration(milliseconds: 200),
    double hoverScale = 1.05,
    double hoverElevation = 8.0,
    double normalElevation = 2.0,
  }) {
    return StatefulBuilder(
      builder: (context, setState) {
        bool isHovered = false;

        return MouseRegion(
          onEnter: (_) => setState(() => isHovered = true),
          onExit: (_) => setState(() => isHovered = false),
          child: Transform.scale(
            scale: isHovered ? hoverScale : 1.0,
            child: AnimatedContainer(
              duration: duration,
              child: Card(
                elevation: isHovered ? hoverElevation : normalElevation,
                child: child,
              ),
            ),
          ),
        );
      },
    );
  }

  /// Animated card with color change
  static Widget animatedCardWithColor({
    required Widget child,
    required Color normalColor,
    required Color hoverColor,
    Duration duration = const Duration(milliseconds: 200),
  }) {
    return StatefulBuilder(
      builder: (context, setState) {
        bool isHovered = false;

        return MouseRegion(
          onEnter: (_) => setState(() => isHovered = true),
          onExit: (_) => setState(() => isHovered = false),
          child: AnimatedContainer(
            duration: duration,
            decoration: BoxDecoration(
              color: isHovered ? hoverColor : normalColor,
            ),
            child: child,
          ),
        );
      },
    );
  }

  // =========================================================================
  // BUTTON ANIMATIONS
  // =========================================================================

  /// Animated button with ripple effect
  static Widget animatedButton({
    required Widget child,
    required VoidCallback onPressed,
    Duration duration = const Duration(milliseconds: 150),
    double pressScale = 0.95,
  }) {
    return StatefulBuilder(
      builder: (context, setState) {
        bool isPressed = false;

        return GestureDetector(
          onTapDown: (_) => setState(() => isPressed = true),
          onTapUp: (_) => setState(() => isPressed = false),
          onTapCancel: () => setState(() => isPressed = false),
          onTap: onPressed,
          child: Transform.scale(
            scale: isPressed ? pressScale : 1.0,
            child: AnimatedContainer(
              duration: duration,
              child: child,
            ),
          ),
        );
      },
    );
  }

  /// Animated button with color change
  static Widget animatedButtonWithColor({
    required Widget child,
    required VoidCallback onPressed,
    required Color normalColor,
    required Color pressedColor,
    Duration duration = const Duration(milliseconds: 150),
  }) {
    return StatefulBuilder(
      builder: (context, setState) {
        bool isPressed = false;

        return GestureDetector(
          onTapDown: (_) => setState(() => isPressed = true),
          onTapUp: (_) => setState(() => isPressed = false),
          onTapCancel: () => setState(() => isPressed = false),
          onTap: onPressed,
          child: AnimatedContainer(
            duration: duration,
            decoration: BoxDecoration(
              color: isPressed ? pressedColor : normalColor,
            ),
            child: child,
          ),
        );
      },
    );
  }

  // =========================================================================
  // PROGRESS ANIMATIONS
  // =========================================================================

  /// Animated progress bar
  static Widget animatedProgressBar({
    required double progress,
    Duration duration = const Duration(milliseconds: 500),
    Curve curve = Curves.easeInOut,
    Color? backgroundColor,
    Color? valueColor,
    double height = 4.0,
  }) {
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0.0, end: progress),
      duration: duration,
      curve: curve,
      builder: (context, value, child) {
        return LinearProgressIndicator(
          value: value,
          backgroundColor: backgroundColor,
          valueColor: AlwaysStoppedAnimation<Color>(
            valueColor ?? Theme.of(context).colorScheme.primary,
          ),
          minHeight: height,
        );
      },
    );
  }

  /// Animated circular progress
  static Widget animatedCircularProgress({
    required double progress,
    Duration duration = const Duration(milliseconds: 500),
    Curve curve = Curves.easeInOut,
    Color? backgroundColor,
    Color? valueColor,
    double size = 40.0,
    double strokeWidth = 4.0,
  }) {
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0.0, end: progress),
      duration: duration,
      curve: curve,
      builder: (context, value, child) {
        return SizedBox(
          width: size,
          height: size,
          child: CircularProgressIndicator(
            value: value,
            backgroundColor: backgroundColor,
            valueColor: AlwaysStoppedAnimation<Color>(
              valueColor ?? Theme.of(context).colorScheme.primary,
            ),
            strokeWidth: strokeWidth,
          ),
        );
      },
    );
  }

  // =========================================================================
  // UTILITY METHODS
  // =========================================================================

  /// Create a custom animation controller
  static AnimationController createAnimationController({
    required TickerProvider vsync,
    Duration duration = const Duration(milliseconds: 300),
  }) {
    return AnimationController(duration: duration, vsync: vsync);
  }

  /// Create a custom animation
  static Animation<double> createAnimation({
    required AnimationController controller,
    double begin = 0.0,
    double end = 1.0,
    Curve curve = Curves.easeInOut,
  }) {
    return Tween<double>(
      begin: begin,
      end: end,
    ).animate(CurvedAnimation(parent: controller, curve: curve));
  }

  /// Animate a value with custom tween
  static Widget animateValue<T>({
    required T begin,
    required T end,
    required Duration duration,
    required Widget Function(BuildContext, T, Widget?) builder,
    Curve curve = Curves.easeInOut,
  }) {
    return TweenAnimationBuilder<T>(
      tween: Tween(begin: begin, end: end),
      duration: duration,
      curve: curve,
      builder: builder,
    );
  }
}
