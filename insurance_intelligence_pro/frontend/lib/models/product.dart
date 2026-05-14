class GuidanceRef {
  final String label;     // user-facing "SSAP 51R" / "ASC 944-40" / "FAS 60"
  final String framework; // SSAP / ASC944 / PCAOB
  final String id;        // resolves to Standard.id in the catalog
  final String note;
  const GuidanceRef({
    required this.label,
    required this.framework,
    required this.id,
    required this.note,
  });
  factory GuidanceRef.fromJson(Map<String, dynamic> j) => GuidanceRef(
        label: j['label']?.toString() ?? '',
        framework: j['framework']?.toString() ?? '',
        id: j['id']?.toString() ?? '',
        note: j['note']?.toString() ?? '',
      );
}

class ProductCategory {
  final String key;
  final String label;
  const ProductCategory({required this.key, required this.label});
  factory ProductCategory.fromJson(Map<String, dynamic> j) => ProductCategory(
        key: j['key']?.toString() ?? '',
        label: j['label']?.toString() ?? '',
      );
}

class InsuranceProduct {
  final String id;
  final String name;
  final String category;
  final String classification; // "Insurance Contract" | "Investment-Type Contract"
  final String riskType;
  final String duration;
  final String briefIntro;
  final String description;
  final List<String> keyFeatures;
  final List<GuidanceRef> guidance;
  final List<String> kpis;
  final double? marketSizeUsd;
  final String marketNote;
  final String sourceLink;
  final List<String> tags;

  InsuranceProduct({
    required this.id,
    required this.name,
    required this.category,
    required this.classification,
    required this.riskType,
    required this.duration,
    required this.briefIntro,
    required this.description,
    required this.keyFeatures,
    required this.guidance,
    required this.kpis,
    this.marketSizeUsd,
    required this.marketNote,
    required this.sourceLink,
    required this.tags,
  });

  factory InsuranceProduct.fromJson(Map<String, dynamic> j) => InsuranceProduct(
        id: j['id']?.toString() ?? '',
        name: j['name']?.toString() ?? '',
        category: j['category']?.toString() ?? '',
        classification: j['classification']?.toString() ?? '',
        riskType: j['risk_type']?.toString() ?? '',
        duration: j['duration']?.toString() ?? '',
        briefIntro: j['brief_intro']?.toString() ?? '',
        description: j['description']?.toString() ?? '',
        keyFeatures: ((j['key_features'] as List?) ?? [])
            .map((e) => e.toString())
            .toList(),
        guidance: ((j['guidance'] as List?) ?? [])
            .map((e) =>
                GuidanceRef.fromJson(Map<String, dynamic>.from(e as Map)))
            .toList(),
        kpis: ((j['kpis'] as List?) ?? []).map((e) => e.toString()).toList(),
        marketSizeUsd: (j['market_size_usd'] as num?)?.toDouble(),
        marketNote: j['market_note']?.toString() ?? '',
        sourceLink: j['source_link']?.toString() ?? '',
        tags: ((j['tags'] as List?) ?? []).map((e) => e.toString()).toList(),
      );

  bool get isInsuranceContract =>
      classification.toLowerCase().contains('insurance');

  /// Search corpus across all fields for the global / library search.
  String get searchCorpus => [
        id,
        name,
        category,
        classification,
        riskType,
        duration,
        briefIntro,
        description,
        ...keyFeatures,
        ...kpis,
        ...tags,
        for (final g in guidance) '${g.label} ${g.note}',
      ].join(' ').toLowerCase();
}

class ProductLibrary {
  final List<ProductCategory> categories;
  final List<InsuranceProduct> products;
  const ProductLibrary({required this.categories, required this.products});

  factory ProductLibrary.fromJson(Map<String, dynamic> j) => ProductLibrary(
        categories: ((j['categories'] as List?) ?? [])
            .map((e) =>
                ProductCategory.fromJson(Map<String, dynamic>.from(e as Map)))
            .toList(),
        products: ((j['products'] as List?) ?? [])
            .map((e) =>
                InsuranceProduct.fromJson(Map<String, dynamic>.from(e as Map)))
            .toList(),
      );

  List<InsuranceProduct> byCategory(String key) =>
      products.where((p) => p.category == key).toList();
}
