/// Represents a DVD title from listTitles JSON.
class DvdTitle {
  const DvdTitle({
    required this.titleNumber,
    this.chapterCount = 0,
    this.angleCount = 1,
  });

  final int titleNumber;
  final int chapterCount;
  final int angleCount;

  static List<DvdTitle> fromJson(String json) {
    if (json.isEmpty || json == '[]') return [];
    try {
      final decoded = _tryDecode(json);
      if (decoded.isEmpty) {
        return [const DvdTitle(titleNumber: 1)];
      }
      return decoded;
    } catch (_) {
      return [const DvdTitle(titleNumber: 1)];
    }
  }

  static List<DvdTitle> _tryDecode(String json) {
    final list = <DvdTitle>[];
    final inner = json.replaceAll(RegExp(r'^\s*\[\s*|\s*\]\s*$'), '').trim();
    if (inner.isEmpty) return list;
    var i = 0;
    while (i < inner.length) {
      if (inner[i] == '{') {
        var end = inner.indexOf('}', i);
        if (end > i) {
          final obj = inner.substring(i, end + 1);
          final numMatch = RegExp(r'"titleNumber"|"n"\s*:\s*(\d+)').firstMatch(obj);
          final num = numMatch != null ? int.tryParse(numMatch.group(1) ?? '1') ?? 1 : list.length + 1;
          final chMatch = RegExp(r'"chapters"|"c"\s*:\s*(\d+)').firstMatch(obj);
          list.add(DvdTitle(
            titleNumber: num,
            chapterCount: chMatch != null ? int.tryParse(chMatch.group(1) ?? '0') ?? 0 : 0,
          ));
          i = end + 1;
        } else {
          i++;
        }
      } else {
        i++;
      }
    }
    return list;
  }
}
