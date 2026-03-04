import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Skeleton state for the DNS feature.
class DnsState {
  const DnsState();
}

final dnsViewModelProvider =
    NotifierProvider<DnsViewModel, DnsState>(DnsViewModel.new);

class DnsViewModel extends Notifier<DnsState> {
  @override
  DnsState build() => const DnsState();
}
