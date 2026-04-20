import 'dart:async';
import 'dart:math';

class NiftyFeed {
  NiftyFeed({double start = 24500, this.tickMs = 250, int? seed})
      : _price = start,
        _rng = Random(seed);

  final int tickMs;
  double _price;
  final Random _rng;
  Timer? _timer;
  final _ctrl = StreamController<double>.broadcast();

  Stream<double> get stream => _ctrl.stream;
  double get price => _price;

  void start() {
    _timer ??= Timer.periodic(Duration(milliseconds: tickMs), (_) {
      final u1 = 1 - _rng.nextDouble();
      final u2 = 1 - _rng.nextDouble();
      final z = sqrt(-2 * log(u1)) * cos(2 * pi * u2);
      final drift = -(_price - 24500) * 0.0008;
      _price = (_price + drift + z * 2.5).clamp(22000, 27000);
      _ctrl.add(_price);
    });
  }

  void dispose() {
    _timer?.cancel();
    _ctrl.close();
  }
}
