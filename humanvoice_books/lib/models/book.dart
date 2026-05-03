import 'dart:convert';

class Book {
  final String id; // stable hash of file path
  final String filePath;
  final String title;
  final String? author;
  final String? coverPath; // local extracted cover, null if EPUB has none
  final int lastReadPage;
  final DateTime importedAt;

  const Book({
    required this.id,
    required this.filePath,
    required this.title,
    this.author,
    this.coverPath,
    this.lastReadPage = 0,
    required this.importedAt,
  });

  Book copyWith({
    String? title,
    String? author,
    String? coverPath,
    int? lastReadPage,
  }) =>
      Book(
        id: id,
        filePath: filePath,
        title: title ?? this.title,
        author: author ?? this.author,
        coverPath: coverPath ?? this.coverPath,
        lastReadPage: lastReadPage ?? this.lastReadPage,
        importedAt: importedAt,
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'filePath': filePath,
        'title': title,
        'author': author,
        'coverPath': coverPath,
        'lastReadPage': lastReadPage,
        'importedAt': importedAt.toIso8601String(),
      };

  static Book fromJson(Map<String, dynamic> j) => Book(
        id: j['id'] as String,
        filePath: j['filePath'] as String,
        title: j['title'] as String,
        author: j['author'] as String?,
        coverPath: j['coverPath'] as String?,
        lastReadPage: (j['lastReadPage'] as num?)?.toInt() ?? 0,
        importedAt: DateTime.parse(j['importedAt'] as String),
      );

  static String encodeAll(List<Book> list) =>
      jsonEncode(list.map((b) => b.toJson()).toList());

  static List<Book> decodeAll(String raw) {
    final list = jsonDecode(raw);
    if (list is! List) return <Book>[];
    // .toList() returns a growable list by default; importers will mutate it.
    return list
        .whereType<Map<String, dynamic>>()
        .map(Book.fromJson)
        .toList();
  }
}
