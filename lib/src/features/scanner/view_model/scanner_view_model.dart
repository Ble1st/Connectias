import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Skeleton state for the Scanner feature.
class ScannerState {
  const ScannerState();
}

final scannerViewModelProvider =
    NotifierProvider<ScannerViewModel, ScannerState>(ScannerViewModel.new);

class ScannerViewModel extends Notifier<ScannerState> {
  @override
  ScannerState build() => const ScannerState();
}
