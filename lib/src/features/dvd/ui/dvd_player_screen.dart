import 'package:flutter/material.dart';

import '../data/dvd_title.dart';
import '../services/dvd_service.dart';

/// DVD player screen: title list and transport controls.
class DvdPlayerScreen extends StatefulWidget {
  const DvdPlayerScreen({
    super.key,
    required this.deviceId,
    this.deviceName,
  });

  final String deviceId;
  final String? deviceName;

  @override
  State<DvdPlayerScreen> createState() => _DvdPlayerScreenState();
}

class _DvdPlayerScreenState extends State<DvdPlayerScreen> {
  final _dvdService = DvdService();
  int? _dvdHandle;
  List<DvdTitle> _titles = [];
  bool _loading = true;
  String? _error;
  bool _playing = false;
  int? _selectedTitleId;

  @override
  void initState() {
    super.initState();
    _openDvd();
  }

  Future<void> _openDvd() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final handle = await _dvdService.openDvd(widget.deviceId);
      final json = await _dvdService.listTitles(handle);
      final titles = DvdTitle.fromJson(json);
      if (mounted) {
        setState(() {
          _dvdHandle = handle;
          _titles = titles;
          _loading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e.toString();
          _loading = false;
        });
      }
    }
  }

  @override
  void dispose() {
    if (_dvdHandle != null) {
      _dvdService.closeDvd(_dvdHandle!);
    }
    super.dispose();
  }

  Future<void> _playTitle(int titleId) async {
    if (_dvdHandle == null) return;
    try {
      await _dvdService.loadDvd(_dvdHandle!);
      await _dvdService.playTitle(titleId);
      setState(() {
        _playing = true;
        _selectedTitleId = titleId;
      });
    } catch (e) {
      setState(() => _error = e.toString());
    }
  }

  Future<void> _pause() async {
    await _dvdService.pause();
    setState(() => _playing = false);
  }

  Future<void> _resume() async {
    await _dvdService.resume();
    setState(() => _playing = true);
  }

  Future<void> _stop() async {
    await _dvdService.stop();
    setState(() {
      _playing = false;
      _selectedTitleId = null;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.deviceName ?? 'DVD Player'),
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.error_outline, size: 48, color: Theme.of(context).colorScheme.error),
                        const SizedBox(height: 16),
                        Text(_error!, textAlign: TextAlign.center),
                        const SizedBox(height: 16),
                        FilledButton(
                          onPressed: () => Navigator.pop(context),
                          child: const Text('Zurück'),
                        ),
                      ],
                    ),
                  ),
                )
              : Column(
                  children: [
                    Expanded(
                      child: ListView.builder(
                        itemCount: _titles.length,
                        itemBuilder: (context, i) {
                          final t = _titles[i];
                          return ListTile(
                            leading: const Icon(Icons.movie),
                            title: Text('Titel ${t.titleNumber}'),
                            subtitle: Text('${t.chapterCount} Kapitel'),
                            onTap: () => _playTitle(t.titleNumber),
                          );
                        },
                      ),
                    ),
                    if (_playing || _selectedTitleId != null)
                      Container(
                        padding: const EdgeInsets.all(16),
                        color: Theme.of(context).colorScheme.surfaceContainerHighest,
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            IconButton(
                              icon: const Icon(Icons.stop),
                              onPressed: _stop,
                            ),
                            if (_playing)
                              IconButton(
                                icon: const Icon(Icons.pause),
                                onPressed: _pause,
                              )
                            else
                              IconButton(
                                icon: const Icon(Icons.play_arrow),
                                onPressed: _resume,
                              ),
                          ],
                        ),
                      ),
                  ],
                ),
    );
  }
}
