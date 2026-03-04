import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Skeleton state for the Password feature.
class PasswordState {
  const PasswordState();
}

final passwordViewModelProvider =
    NotifierProvider<PasswordViewModel, PasswordState>(PasswordViewModel.new);

class PasswordViewModel extends Notifier<PasswordState> {
  @override
  PasswordState build() => const PasswordState();
}
