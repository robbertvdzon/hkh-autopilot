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

class AggregatedNewsEntity {
  const AggregatedNewsEntity({
    required this.type,
    required this.label,
    required this.itemCount,
  });

  factory AggregatedNewsEntity.fromJson(Map<String, dynamic> json) =>
      AggregatedNewsEntity(
        type: json['type'] as String,
        label: json['label'] as String,
        itemCount: json['itemCount'] as int,
      );

  final String type;
  final String label;
  final int itemCount;
}

class NewsSearchResult {
  const NewsSearchResult({
    required this.items,
    required this.total,
    required this.entities,
  });

  factory NewsSearchResult.fromJson(Map<String, dynamic> json) =>
      NewsSearchResult(
        items: (json['items'] as List<dynamic>)
            .map(
              (item) => LatestNewsItem.fromJson(item as Map<String, dynamic>),
            )
            .toList(growable: false),
        total: json['total'] as int,
        entities: (json['entities'] as List<dynamic>)
            .map(
              (entity) =>
                  AggregatedNewsEntity.fromJson(entity as Map<String, dynamic>),
            )
            .toList(growable: false),
      );

  final List<LatestNewsItem> items;
  final int total;
  final List<AggregatedNewsEntity> entities;
}

abstract interface class LatestNewsSource {
  /// Zoekt gepubliceerd nieuws op. [q] is een optionele vrije zoekterm
  /// (titel/samenvatting), [entity] een optioneel canoniek entiteitslabel.
  /// Beide filters worden als AND gecombineerd door de backend; `entities` in
  /// het resultaat blijft altijd de van het filter onafhankelijke, geaggregeerde
  /// telling over alle gepubliceerde berichten.
  Future<NewsSearchResult> loadLatestNews({String? q, String? entity});
}
