import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Skeleton state for the Network feature.
class NetworkState {
  const NetworkState();
}

final networkViewModelProvider =
    NotifierProvider<NetworkViewModel, NetworkState>(NetworkViewModel.new);

class NetworkViewModel extends Notifier<NetworkState> {
  @override
  NetworkState build() => const NetworkState();
}
