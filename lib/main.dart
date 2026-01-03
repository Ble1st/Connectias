import 'package:flutter/material.dart';
import 'screens/home_screen.dart';
import 'services/rust_service.dart';
import 'services/log_service.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  // Initialize Rust service and database
  try {
    await RustService.initialize();
    debugPrint('Rust service initialized successfully');
    
    // Log app start with timestamp
    try {
      final logService = LogService();
      final now = DateTime.now();
      await logService.logMessage(
        level: 'INFO',
        message: 'App gestartet - ${now.toIso8601String()}',
        module: 'main',
      );
      debugPrint('App start logged successfully');
    } catch (logError) {
      debugPrint('Failed to log app start: $logError');
      // Don't fail the app if logging fails
    }
  } catch (e, stackTrace) {
    debugPrint('Error initializing Rust service: $e');
    debugPrint('Stack trace: $stackTrace');
    // Show error to user
    runApp(MaterialApp(
      home: Scaffold(
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.error, color: Colors.red, size: 48),
              const SizedBox(height: 16),
              const Text(
                'Fehler beim Initialisieren der Rust-Bibliothek',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 8),
              Padding(
                padding: const EdgeInsets.all(16.0),
                child: Text(
                  '$e',
                  textAlign: TextAlign.center,
                  style: const TextStyle(fontSize: 12),
                ),
              ),
            ],
          ),
        ),
      ),
    ));
    return;
  }
  
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Connectias',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
        useMaterial3: true,
      ),
      home: const HomeScreen(),
    );
  }
}
