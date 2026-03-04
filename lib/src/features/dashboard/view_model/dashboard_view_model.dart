import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Skeleton state for the dashboard (navigation-only screen).
class DashboardState {
  const DashboardState();
}

final dashboardViewModelProvider =
    NotifierProvider<DashboardViewModel, DashboardState>(DashboardViewModel.new);

class DashboardViewModel extends Notifier<DashboardState> {
  @override
  DashboardState build() => const DashboardState();
}
