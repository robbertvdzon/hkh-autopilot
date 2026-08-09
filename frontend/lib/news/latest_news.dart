class NewsEntity {
  const NewsEntity({required this.type, required this.label});

  factory NewsEntity.fromJson(Map<String, dynamic> json) => NewsEntity(
    type: json['type'] as String,
    label: json['label'] as String,
  );

  final String type;
  final String label;
}

class LatestNewsItem {
  const LatestNewsItem({
    required this.id,
    required this.title,
    required this.message,
    required this.publishedAt,
    this.entities = const [],
    this.source,
  });

  factory LatestNewsItem.fromJson(Map<String, dynamic> json) => LatestNewsItem(
    id: json['id'] as int,
    title: json['title'] as String,
    message: json['message'] as String,
    publishedAt: DateTime.parse(json['publishedAt'] as String),
    entities: (json['entities'] as List<dynamic>? ?? const [])
        .map((entity) => NewsEntity.fromJson(entity as Map<String, dynamic>))
        .toList(growable: false),
    source: json['source'] as String?,
  );

  final int id;
  final String title;
  final String message;
  final DateTime publishedAt;
  final List<NewsEntity> entities;
  final String? source;
}

abstract interface class LatestNewsSource {
  Future<List<LatestNewsItem>> loadLatestNews();
}
