import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Skeleton state for the Settings feature.
class SettingsState {
  const SettingsState();
}

final settingsViewModelProvider =
    NotifierProvider<SettingsViewModel, SettingsState>(SettingsViewModel.new);

class SettingsViewModel extends Notifier<SettingsState> {
  @override
  SettingsState build() => const SettingsState();
}
