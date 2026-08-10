import 'dart:async';

import 'package:flutter/material.dart';

import '../auth/admin_session.dart';
import 'admin_record_intake.dart';
import 'external_archive_preview_panel.dart';

/// Formulier voor de intake van precies één lokaal collectierecord.
///
/// Status wordt bewust via tekst plus een aria-live-statusgebied gecommuniceerd (niet uitsluitend
/// kleur) en is een ander patroon dan de passieve `SemanticsRole.status`-conventie elders in de
/// app: na een mislukte validatie verplaatst de toetsenbordfocus naar de foutsamenvatting en is
/// elke fout programmatisch aan het bijbehorende veld gekoppeld.
class RecordIntakeForm extends StatefulWidget {
  const RecordIntakeForm({
    required this.identity,
    required this.recordIntakeSource,
    super.key,
  });

  final AdminIdentity identity;
  final AdminRecordIntakeSource recordIntakeSource;

  @override
  State<RecordIntakeForm> createState() => _RecordIntakeFormState();
}

const Map<String, String> _fieldLabels = {
  'localIdentifier': 'Lokale identifier',
  'title': 'Titel',
  'description': 'Beschrijving',
  'dating': 'Datering',
  'provenance': 'Herkomst',
  'rightsStatus': 'Rechtenstatus',
  'privacyClassification': 'Privacyclassificatie',
  'accessUrl': 'Toegangs- of permalink',
};

const List<String> _privacyClassifications = [
  'geen persoonsgegevens',
  'mogelijk persoonsgegevens',
  'persoonsgegevens',
];

const List<String> _linkUncertainties = ['laag', 'middel', 'hoog'];

const List<String> _deceasedStatuses = ['onbekend', 'overleden', 'levend'];

/// Cooldown tussen opeenvolgende wijzigingen van de duurzame URL voordat de gedebouncte
/// previewaanroep daadwerkelijk verstuurd wordt.
const _archivePreviewDebounce = Duration(milliseconds: 400);

class _RecordIntakeFormState extends State<RecordIntakeForm> {
  final _localIdentifierController = TextEditingController();
  final _titleController = TextEditingController();
  final _descriptionController = TextEditingController();
  final _datingController = TextEditingController();
  final _provenanceController = TextEditingController();
  final _rightsStatusController = TextEditingController();
  final _accessUrlController = TextEditingController();
  final _durableUrlController = TextEditingController();
  final _linkRationaleController = TextEditingController();
  final _durableUrlFocusNode = FocusNode();

  String? _privacyClassification;
  String? _linkUncertainty;
  String _deceasedStatus = 'onbekend';
  bool _nextOfKinConfirmed = false;

  Timer? _archivePreviewDebounceTimer;
  int _archivePreviewRequestId = 0;
  String? _lastPreviewedDurableUrl;
  bool _archivePreviewLoading = false;
  RecordIntakeExternalArchivePreviewResult? _archivePreviewResult;
  bool? _confirmExternalArchiveData;

  final Map<String, FocusNode> _focusNodes = {
    for (final key in _fieldLabels.keys) key: FocusNode(),
  };
  final _summaryFocus = FocusNode(skipTraversal: false);

  Map<String, String> _fieldErrors = {};
  String? _privacyBlockedMessage;
  String? _statusText;
  bool _statusIsError = false;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _durableUrlController.addListener(_onDurableUrlChanged);
    _durableUrlFocusNode.addListener(_onDurableUrlFocusChanged);
  }

  @override
  void dispose() {
    _archivePreviewDebounceTimer?.cancel();
    _durableUrlController.removeListener(_onDurableUrlChanged);
    _durableUrlFocusNode.removeListener(_onDurableUrlFocusChanged);
    _localIdentifierController.dispose();
    _titleController.dispose();
    _descriptionController.dispose();
    _datingController.dispose();
    _provenanceController.dispose();
    _rightsStatusController.dispose();
    _accessUrlController.dispose();
    _durableUrlController.dispose();
    _linkRationaleController.dispose();
    _durableUrlFocusNode.dispose();
    for (final node in _focusNodes.values) {
      node.dispose();
    }
    _summaryFocus.dispose();
    super.dispose();
  }

  void _onDurableUrlChanged() {
    _archivePreviewDebounceTimer?.cancel();
    _archivePreviewDebounceTimer = Timer(
      _archivePreviewDebounce,
      _fetchArchivePreview,
    );
  }

  void _onDurableUrlFocusChanged() {
    if (_durableUrlFocusNode.hasFocus) return;
    _archivePreviewDebounceTimer?.cancel();
    _fetchArchivePreview();
  }

  Future<void> _fetchArchivePreview({bool force = false}) async {
    final durableUrl = _durableUrlController.text.trim();
    if (!force && durableUrl == _lastPreviewedDurableUrl) return;
    _lastPreviewedDurableUrl = durableUrl;

    final requestId = ++_archivePreviewRequestId;
    setState(() {
      _archivePreviewLoading = true;
      _confirmExternalArchiveData = null;
    });

    RecordIntakeExternalArchivePreviewResult result;
    try {
      result = await widget.recordIntakeSource.previewExternalArchiveData(
        durableUrl: durableUrl,
      );
    } catch (_) {
      result = const RecordIntakeExternalArchivePreviewResult(
        status: RecordIntakeExternalArchivePreviewStatus.unreachable,
      );
    }
    if (!mounted || requestId != _archivePreviewRequestId) return;
    setState(() {
      _archivePreviewLoading = false;
      _archivePreviewResult = result;
    });
  }

  Map<String, String> _validateClientSide() {
    final errors = <String, String>{};
    if (_localIdentifierController.text.trim().isEmpty) {
      errors['localIdentifier'] = 'Vul een lokale identifier in.';
    }
    if (_titleController.text.trim().isEmpty &&
        _descriptionController.text.trim().isEmpty) {
      errors['title'] = 'Vul een titel of beschrijving in.';
      errors['description'] = 'Vul een titel of beschrijving in.';
    }
    if (_datingController.text.trim().isEmpty) {
      errors['dating'] = 'Vul een datering in.';
    }
    if (_provenanceController.text.trim().isEmpty) {
      errors['provenance'] = 'Vul de herkomst in.';
    }
    if (_rightsStatusController.text.trim().isEmpty) {
      errors['rightsStatus'] = 'Vul de rechtenstatus in.';
    }
    if (_privacyClassification == null) {
      errors['privacyClassification'] = 'Kies een privacyclassificatie.';
    }
    if (!_isAbsoluteHttpUrl(_accessUrlController.text)) {
      errors['accessUrl'] =
          'Vul een geldige toegangs- of permalink in (http of https).';
    }
    return errors;
  }

  bool _isAbsoluteHttpUrl(String raw) {
    final candidate = raw.trim();
    if (candidate.isEmpty) return false;
    final uri = Uri.tryParse(candidate);
    if (uri == null || !uri.isAbsolute) return false;
    return (uri.scheme == 'http' || uri.scheme == 'https') &&
        uri.host.isNotEmpty;
  }

  Future<void> _submit() async {
    final clientErrors = _validateClientSide();
    if (clientErrors.isNotEmpty) {
      setState(() {
        _fieldErrors = clientErrors;
        _privacyBlockedMessage = null;
        _statusIsError = true;
        _statusText =
            'Er zijn ${clientErrors.length} fouten gevonden. Corrigeer de gemarkeerde velden.';
      });
      _focusSummary();
      return;
    }

    setState(() {
      _saving = true;
      _fieldErrors = {};
      _privacyBlockedMessage = null;
      _statusText = null;
    });

    try {
      final result = await widget.recordIntakeSource.create(
        identity: widget.identity,
        input: _buildInput(),
      );
      if (!mounted) return;
      final confirmedExternalArchiveData = _confirmExternalArchiveData == true;
      _clearForm();
      setState(() {
        _statusIsError = false;
        final parts = [
          result.externalLinkCreated
              ? 'Het record is opgeslagen als intern concept en de externe conceptkoppeling is aangemaakt.'
              : 'Het record is opgeslagen als intern concept.',
        ];
        if (confirmedExternalArchiveData &&
            result.externalArchiveDataReason != null) {
          parts.add(result.externalArchiveDataReason!);
        }
        _statusText = parts.join(' ');
      });
    } on RecordIntakeFieldErrors catch (error) {
      if (!mounted) return;
      setState(() {
        _fieldErrors = {
          for (final path in error.fieldErrors)
            path: 'Controleer dit veld: ${_fieldLabelFor(path)}.',
        };
        _statusIsError = true;
        _statusText =
            'Er zijn ${error.fieldErrors.length} fouten gevonden. Corrigeer de gemarkeerde velden.';
      });
      _focusSummary();
    } on RecordIntakePrivacyBlocked {
      if (!mounted) return;
      setState(() {
        _privacyBlockedMessage =
            'Geweigerd: alleen records zonder persoonsgegevens worden geaccepteerd (PRIVACY_CLASSIFICATION_BLOCKED).';
        _statusIsError = true;
        _statusText = _privacyBlockedMessage;
      });
      _focusSummary();
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _statusIsError = true;
        _statusText = 'Opslaan is mislukt. Probeer het opnieuw.';
      });
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  String _fieldLabelFor(String path) => _fieldLabels[path] ?? path;

  void _focusSummary() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _summaryFocus.requestFocus();
    });
  }

  RecordIntakeInput _buildInput() {
    final durableUrl = _durableUrlController.text.trim();
    final linkRationale = _linkRationaleController.text.trim();
    final hasExternalLink =
        durableUrl.isNotEmpty &&
        linkRationale.isNotEmpty &&
        _linkUncertainty != null;
    return RecordIntakeInput(
      localIdentifier: _localIdentifierController.text.trim(),
      title: _titleController.text.trim().isEmpty
          ? null
          : _titleController.text.trim(),
      description: _descriptionController.text.trim().isEmpty
          ? null
          : _descriptionController.text.trim(),
      dating: _datingController.text.trim(),
      provenance: _provenanceController.text.trim(),
      rightsStatus: _rightsStatusController.text.trim(),
      privacyClassification: _privacyClassification!,
      accessUrl: _accessUrlController.text.trim(),
      externalLink: hasExternalLink
          ? RecordIntakeExternalLinkInput(
              durableUrl: durableUrl,
              linkRationale: linkRationale,
              uncertainty: _linkUncertainty!,
            )
          : null,
      deceasedStatus: _deceasedStatus,
      nextOfKinConfirmed: _nextOfKinConfirmed,
      confirmExternalArchiveData: _confirmExternalArchiveData,
    );
  }

  void _clearForm() {
    _localIdentifierController.clear();
    _titleController.clear();
    _descriptionController.clear();
    _datingController.clear();
    _provenanceController.clear();
    _rightsStatusController.clear();
    _accessUrlController.clear();
    _durableUrlController.clear();
    _linkRationaleController.clear();
    _privacyClassification = null;
    _linkUncertainty = null;
    _deceasedStatus = 'onbekend';
    _nextOfKinConfirmed = false;
    _archivePreviewDebounceTimer?.cancel();
    _archivePreviewResult = null;
    _archivePreviewLoading = false;
    _confirmExternalArchiveData = null;
    _lastPreviewedDurableUrl = null;
    _archivePreviewRequestId++;
  }

  @override
  Widget build(BuildContext context) {
    final errorColor = Theme.of(context).colorScheme.error;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          'Nieuwe record-intake',
          style: Theme.of(context).textTheme.headlineSmall,
        ),
        const SizedBox(height: 8),
        const Text(
          'Lever precies één lokaal collectierecord aan als intern concept.',
        ),
        const SizedBox(height: 20),
        _buildErrorSummary(errorColor),
        _field(key: 'localIdentifier', controller: _localIdentifierController),
        const SizedBox(height: 12),
        _field(key: 'title', controller: _titleController),
        const SizedBox(height: 12),
        _field(
          key: 'description',
          controller: _descriptionController,
          minLines: 3,
          maxLines: 8,
        ),
        const SizedBox(height: 12),
        _field(key: 'dating', controller: _datingController),
        const SizedBox(height: 12),
        _field(key: 'provenance', controller: _provenanceController),
        const SizedBox(height: 12),
        _field(key: 'rightsStatus', controller: _rightsStatusController),
        const SizedBox(height: 12),
        _privacyDropdown(),
        const SizedBox(height: 12),
        _field(key: 'accessUrl', controller: _accessUrlController),
        const SizedBox(height: 20),
        Text(
          'Optionele externe conceptkoppeling',
          style: Theme.of(context).textTheme.titleMedium,
        ),
        const SizedBox(height: 4),
        const Text(
          'Alleen aangemaakt als duurzame URL, koppelmotivering en onzekerheid alle drie zijn ingevuld.',
        ),
        const SizedBox(height: 8),
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: TextFormField(
                controller: _durableUrlController,
                focusNode: _durableUrlFocusNode,
                decoration: const InputDecoration(
                  labelText: 'Duurzame URL',
                  border: OutlineInputBorder(),
                ),
              ),
            ),
            const SizedBox(width: 8),
            OutlinedButton(
              key: const Key('archivePreviewFetchButton'),
              onPressed: () {
                _archivePreviewDebounceTimer?.cancel();
                _fetchArchivePreview(force: true);
              },
              child: const Text('Ophalen'),
            ),
          ],
        ),
        if (_archivePreviewLoading || _archivePreviewResult != null) ...[
          const SizedBox(height: 12),
          ExternalArchivePreviewPanel(
            loading: _archivePreviewLoading,
            result: _archivePreviewResult,
            confirmed: _confirmExternalArchiveData,
            onConfirm: () => setState(() => _confirmExternalArchiveData = true),
            onDecline: () =>
                setState(() => _confirmExternalArchiveData = false),
          ),
        ],
        const SizedBox(height: 12),
        TextFormField(
          controller: _linkRationaleController,
          decoration: const InputDecoration(
            labelText: 'Koppelmotivering',
            border: OutlineInputBorder(),
          ),
        ),
        const SizedBox(height: 12),
        DropdownButtonFormField<String>(
          initialValue: _linkUncertainty,
          decoration: const InputDecoration(
            labelText: 'Onzekerheid',
            border: OutlineInputBorder(),
          ),
          items: _linkUncertainties
              .map(
                (value) => DropdownMenuItem(value: value, child: Text(value)),
              )
              .toList(),
          onChanged: (value) => setState(() => _linkUncertainty = value),
        ),
        const SizedBox(height: 20),
        Text(
          'Overlijdensstatus hoofdpersoon',
          style: Theme.of(context).textTheme.titleMedium,
        ),
        const SizedBox(height: 8),
        DropdownButtonFormField<String>(
          key: const Key('deceasedStatus'),
          initialValue: _deceasedStatus,
          decoration: const InputDecoration(
            labelText: 'Overlijdensstatus',
            border: OutlineInputBorder(),
          ),
          items: _deceasedStatuses
              .map(
                (value) => DropdownMenuItem(value: value, child: Text(value)),
              )
              .toList(),
          onChanged: (value) =>
              setState(() => _deceasedStatus = value ?? 'onbekend'),
        ),
        CheckboxListTile(
          key: const Key('nextOfKinConfirmed'),
          value: _nextOfKinConfirmed,
          controlAffinity: ListTileControlAffinity.leading,
          contentPadding: EdgeInsets.zero,
          title: const Text('Bevat gegevens van een nog levende nabestaande'),
          onChanged: (value) =>
              setState(() => _nextOfKinConfirmed = value ?? false),
        ),
        const SizedBox(height: 20),
        if (_statusText != null) ...[
          Semantics(
            liveRegion: true,
            child: Text(
              _statusText!,
              style: TextStyle(
                color: _statusIsError
                    ? errorColor
                    : Theme.of(context).colorScheme.primary,
              ),
            ),
          ),
          const SizedBox(height: 12),
        ],
        FilledButton.icon(
          onPressed: _saving ? null : _submit,
          icon: _saving
              ? const SizedBox.square(
                  dimension: 18,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : const Icon(Icons.upload_file),
          label: const Text('Intake indienen'),
        ),
      ],
    );
  }

  Widget _buildErrorSummary(Color errorColor) {
    if (_fieldErrors.isEmpty && _privacyBlockedMessage == null) {
      return const SizedBox.shrink();
    }
    return Padding(
      padding: const EdgeInsets.only(bottom: 20),
      child: Focus(
        focusNode: _summaryFocus,
        child: Semantics(
          container: true,
          explicitChildNodes: true,
          label: 'Foutsamenvatting',
          child: Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              border: Border.all(color: errorColor),
              borderRadius: BorderRadius.circular(4),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  'Controleer de volgende gegevens',
                  style: TextStyle(
                    fontWeight: FontWeight.bold,
                    color: errorColor,
                  ),
                ),
                const SizedBox(height: 8),
                if (_privacyBlockedMessage != null)
                  Text(
                    _privacyBlockedMessage!,
                    style: TextStyle(color: errorColor),
                  ),
                for (final entry in _fieldErrors.entries)
                  Padding(
                    padding: const EdgeInsets.only(top: 4),
                    child: InkWell(
                      onTap: () => _focusNodes[entry.key]?.requestFocus(),
                      child: Text(
                        '${_fieldLabelFor(entry.key)}: ${entry.value}',
                        style: TextStyle(
                          color: errorColor,
                          decoration: TextDecoration.underline,
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _field({
    required String key,
    required TextEditingController controller,
    int minLines = 1,
    int maxLines = 1,
  }) {
    return TextFormField(
      controller: controller,
      focusNode: _focusNodes[key],
      minLines: minLines,
      maxLines: maxLines,
      decoration: InputDecoration(
        labelText: _fieldLabelFor(key),
        border: const OutlineInputBorder(),
        errorText: _fieldErrors[key],
      ),
    );
  }

  Widget _privacyDropdown() {
    return DropdownButtonFormField<String>(
      key: const Key('privacyClassification'),
      focusNode: _focusNodes['privacyClassification'],
      initialValue: _privacyClassification,
      decoration: InputDecoration(
        labelText: _fieldLabelFor('privacyClassification'),
        border: const OutlineInputBorder(),
        errorText: _fieldErrors['privacyClassification'],
      ),
      items: _privacyClassifications
          .map((value) => DropdownMenuItem(value: value, child: Text(value)))
          .toList(),
      onChanged: (value) => setState(() => _privacyClassification = value),
    );
  }
}
