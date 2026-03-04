import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Skeleton state for the Notes feature.
class NotesState {
  const NotesState();
}

final notesViewModelProvider =
    NotifierProvider<NotesViewModel, NotesState>(NotesViewModel.new);

class NotesViewModel extends Notifier<NotesState> {
  @override
  NotesState build() => const NotesState();
}
