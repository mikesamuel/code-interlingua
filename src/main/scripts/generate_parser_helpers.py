#!/usr/bin/python2.7

import json
import re



_JAVA_PACKAGE = 'com.mikesamuel.cil.ast'


# Maps trait interfaces to metadata fields specified and imports required.
_TRAITS = {
    'CallableDeclaration': (
        (
            ('ExpressionNameResolver', 'expressionNameResolver'),
            ('String', 'methodDescriptor'),
            ('MemberInfo', 'memberInfo'),
        ),
        (
            'com.mikesamuel.cil.ast.meta.ExpressionNameResolver',
            'com.mikesamuel.cil.ast.meta.MemberInfo',
        )),
    'ExpressionNameDeclaration': (
        (
            ('Name', 'declaredExpressionName'),
        ),
        (
            'com.mikesamuel.cil.ast.meta.Name',
        )),
    'ExpressionNameReference': (
        (
            ('Name', 'referencedExpressionName'),
        ),
        (
            'com.mikesamuel.cil.ast.meta.Name',
        )),
    'ExpressionNameScope': (
        (
            ('ExpressionNameResolver', 'expressionNameResolver'),
        ),
        (
            'com.mikesamuel.cil.ast.meta.ExpressionNameResolver',
        )),
    'LimitedScopeElement': (
        (
            ('DeclarationPositionMarker', 'declarationPositionMarker'),
        ),
        (
            'com.mikesamuel.cil.ast.meta.ExpressionNameResolver.DeclarationPositionMarker',
        )),
    'MemberDeclaration': (
        (
            ('MemberInfo', 'memberInfo'),
        ),
        (
            'com.mikesamuel.cil.ast.meta.MemberInfo',
        )),
    'NamePart': (
        (
            ('Name.Type', 'namePartType'),
        ),
        (
            'com.mikesamuel.cil.ast.meta.Name',
        )),
    'TypeDeclaration': (
        (
            ('TypeInfo', 'declaredTypeInfo'),
        ),
        (
            'com.mikesamuel.cil.ast.meta.TypeInfo',
        )),
    'Typed': (
        (
            ('StaticType', 'staticType'),
        ),
        (
            'com.mikesamuel.cil.ast.meta.StaticType',
        )),
    'TypeReference': (
        (
            ('TypeInfo', 'referencedTypeInfo'),
        ),
        (
            'com.mikesamuel.cil.ast.meta.TypeInfo',
        )),
    'TypeScope': (
        (
            ('TypeNameResolver', 'typeNameResolver'),
        ),
        (
            'com.mikesamuel.cil.ast.meta.TypeNameResolver',
        )),
    'WholeType': (
        (
            ('StaticType', 'staticType'),
        ),
        (
            'com.mikesamuel.cil.ast.meta.StaticType',
        )),
    }
_TRAITS['TypeParameterScope'] = _TRAITS['TypeScope']

_CUSTOM_NODE_CONTENT = {
    'ConstructorDeclaration': ('  @Override public String getMethodName() { return "<init>"; }', ()),
    'InstanceInitializer':    ('  @Override public String getMethodName() { return "<init>"; }', ()),
    'StaticInitializer':      ('  @Override public String getMethodName() { return "<clinit>"; }', ()),
    }

_TOKEN_RE = re.compile(
    '|'.join([
        r'//[^\r\n]*',  # Line comment
        r'/[*](?:[^*/]|[*]+(?!=[/*])|[/])*[*]+/',  # Block comment
        r'[ \t]+',  # Whitespace.  Indentation is significant
        r'[\r\n]+',  # Line breaks
        r'"(?:[^"\\]*|\\.)*"',  # Quoted string
        r'[A-Za-z_$]\w*',  # Word
        r'\(@\w+=[^"\'()]*\)',  # Annotation with value
        r'@\w+',  # Valueless annotation
        r'[\[\]\{\}:.]',  # Punctuation
        r'.',  # Other
    ]))

_JAVA_IDENT_START_CHARSET = r'\p{javaJavaIdentifierStart}'

_LINE_BREAK_RE = re.compile(r'(\r\n?|\n)')

_NON_IDENT_CHAR = re.compile(r'[^A-Za-z0-9_$]+')
_UNDERSCORE_NEEDED = re.compile(r'^(?![a-zA-Z_$])|__+')

_TOK_SPACE = 0
_TOK_LINEBREAK = 1
_TOK_COMMENT = 2
_TOK_STRING = 3
_TOK_IDENT = 4
_TOK_ANNOT = 5
_TOK_OTHER = 6

_PTREE_NAME_TO_KIND = {
    None: 'PTree.Kind.SEQUENCE',
    '{}': 'PTree.Kind.REPEATED',
    '[]': 'PTree.Kind.OPTIONAL',
    'lit': 'PTree.Kind.LITERAL',
    'ref': 'PTree.Kind.REFERENCE',
    'nla': 'PTree.Kind.NEGATIVE_LOOKAHEAD',
    }

def _is_ident_start(c):
    return ((('A' <= c and c <= 'Z')
             or ('a' <= c and c <= 'z')
             or (c in ('_', '$'))))

def _is_ident_part(c):
    return ('0' <= c and c <= '9') or _is_ident_start(c)
def _is_annotation(tok_text):
    return tok_text[0] == '@' or tok_text[0:2] == '(@'

def _classify_token((text, _)):
    n = len(text)
    if n == 0:
        return _TOK_SPACE
    t0 = text[0]
    if t0 == '"':
        return _TOK_STRING
    if t0 == '/' and n > 1:
        t1 = text[1]
        if t1 in ('*', '/'):
            return _TOK_COMMENT
    if t0 in (' ', '\t'):
        return _TOK_SPACE
    if t0 in ('\r', '\n'):
        return _TOK_LINEBREAK
    if _is_ident_start(t0):
        return _TOK_IDENT
    if _is_annotation(text):
        return _TOK_ANNOT
    return _TOK_OTHER


# Short names for punctuation strings arranges as a Trie.
# This is used to derive descriptive variant names.
# The trie is represented as a map from punctuation strings to
# values that are pairs (identifier, suffix_trie) where the
# suffix tries match the characters after the key and are
# either None or Maps with the same structure as the Trie.
# A value which is a singleton tuple has an implicit None
# suffix trie.
# An identifier of None indicates that there is no name
# corresponding to the concatenation of the keys though
# the concatenation of the keys might be a prefix of a
# larger punctuation string.
_PUNC_TO_ALNUM = {
    '.': ('dot', {'.': (None, {'.': ('ellip',)})}),
    '[': ('ls',),
    ']': ('rs',),
    '(': ('lp',),
    ')': ('rp',),
    '{': ('lc',),
    '}': ('rc',),
    '&': ('amp', {'&': ('amp2',)}),
    '|': ('pip', {'|': ('pip2',)}),
    '<': ('lt', {'<': ('lt2', {'<': ('lt3',)})}),
    '>': ('gt', {'>': ('gt2', {'>': ('gt3',)})}),
    ',': ('com',),
    '?': ('qm',),
    ';': ('sem',),
    '*': ('str',),
    '=': ('eq',),
    '!': ('bng',),
    '@': ('at',),
    '/': ('fwd',),
    '\\':('bck',),
    ':': ('cln',),
    '-': ('dsh', {'>': ('arr',)}),
    '^': ('hat',),
    '~': ('tld',),
    '%': ('pct',),
    '+': ('pls',),
    '#': ('hsh',),
    '"': ('dq',),
    '\'':('sq',),
    '`': ('tck',),
}

_BRACKET_OTHERS = {
    '(': ')',
    ')': '(',
    '{': '}',
    '}': '{',
    '[': ']',
    ']': '[',
    }

def _to_alnum(s, i):
    """
    Returns an alphanumeric string describing the punctuation in s[i:]
    and the end of the characters described.
    """

    n = len(s)
    if i < n and _is_ident_part(s[i]):
        e = i + 1
        while e < n and _is_ident_part(s[e]):
            e += 1
        return (s[i:e], e)

    best = None
    current = _PUNC_TO_ALNUM
    while i < n and current is not None:
        next_trie = current.get(s[i])
        if next_trie is None:
            break
        assert type(next_trie) is tuple
        desc = next_trie[0]
        if desc is not None:
            best = (desc, i + 1)
        if len(next_trie) == 1:
            break
        current, i = next_trie[1], (i + 1)
    return best

def _java_str_lit(s):
    if type(s) is str:
        s = s.decode('UTF-8')
    assert type(s) is unicode
    return json.dumps(s)

_UNDERSCORE_IDENT_WORD_BREAK = re.compile(r'^_+([A-Za-z])|(?:^|_+)([A-Za-z])')
def _underscores_to_upper_camel(s):
    return _UNDERSCORE_IDENT_WORD_BREAK.sub(
        lambda m: (m.group(1) and '_%s' % m.group(1) or m.group(2)).upper(),
        s)

_CAMEL_SHIFT = re.compile(r'([a-z0-9_$])([A-Z])')
def _camel_to_underscores(s):
    return _CAMEL_SHIFT.sub(
        lambda m: '%s_%s' % (m.group(1), m.group(2)),
        s).upper()

def _jsdoc_of(s, prefix = ' * '):
    return (
        s.replace('&', '&amp;').replace('<', '&lt;')
         .replace('>', '&gt;').replace('*', '&#42;')
         .replace('\n', '\n%s' % prefix))

def _tokens_to_text(toks):
    if len(toks) == 0:
        return ""
    parts = []
    last_line_no = toks[0][1][0]
    for (text, (ln, co, ch)) in toks:
        if ln != last_line_no:
            parts.append('\n')
            last_line_no = ln
        elif len(parts) != 0:
            parts.append(' ')
        parts.append(text)
    return ''.join(parts)


def process_grammar(
        grammar_text,
        source_file_exists,
        emit_java_file,
        dot_out=None,
        verbose=True):
    """
    grammar_text : string -- See ../resources/jsl-19.txt
    source_file_exists : function --
        given a java file true iff it exists under the source directory in the
        right sub-directory for _JAVA_PACKAGE
    emit_java_file : function -- given unqualified name and java_source_code
        writes to a .java file in the generated source directory.
    """

    generator = _java_str_lit(os.path.relpath(__file__))
    line_number, column_number, char_count = 1, 1, 0

    # Split into (token_text, position)
    tokens = []
    for token_text in _TOKEN_RE.findall(grammar_text):
        tokens.append((token_text, (line_number, column_number, char_count)))
        for token_text_part in _LINE_BREAK_RE.split(token_text):
            if token_text_part == '':
                continue
            if token_text_part[0] in ('\n', '\r'):
                line_number += 1
                column_number = 1
            else:
                column_number += len(token_text_part)
        char_count += len(token_text)

    assert char_count == len(grammar_text)  # All chars matched by _TOKEN_RE.

    if verbose:
        print '\nTOKENS'
        for t in tokens:
            print '%r' % (t,)

    # filter out whitespace and comments
    def is_significant(tok):
        return _classify_token(tok) not in (
            _TOK_SPACE, _TOK_COMMENT, _TOK_LINEBREAK)

    filtered_tokens = [t for t in tokens if is_significant(t)]
    if verbose:
        print '\nFILTERED TOKENS'
        for t in filtered_tokens:
            print '%r' % (t,)

    def starts_line((_, pos)):
        (_, column, _) = pos
        return column == 1
    def has_text((text, _), match):
        return text == match
    def is_ident(tok):
        return _classify_token(tok) == _TOK_IDENT
    def same_line((a_text, (a_ln, a_co, a_ch)), (b_text, (b_ln, b_co, b_ch))):
        return a_ln == b_ln

    # We start by splitting based on gross-structure and progress to fine
    # structure so that given an input like
    #
    # (chapter=ChapterName)
    # ProductionName:
    #   {Variant} One
    #
    # we end up with a structure like
    #
    # [
    #   {
    #     'name': 'ChapterName',
    #     'prods':
    #     [
    #       {
    #         'name': 'ProductionName',
    #         'variants':
    #         [
    #           {
    #             'name': 'VariantOne',
    #             'ptree':
    #             [
    #               {
    #                 'name': '{}',
    #                 'ptree':
    #                 [
    #                   { ... }
    # ...
    #
    # The layers of structure are
    # 1. Chapters based on the (chapter=Identifier) markings
    #    used to establish the base node type for productions.
    # 2. Productions which have the syntax
    #    LeftHandSide:
    #        RightHandSide0
    #        RightHandSide1
    # 3. Variants where each indented line after a production
    #    declaration is a variant.  The production is semantically
    #    the analytic disjunction of its variants.
    # 4. Grammar nodes where
    #    {...} means zero or more.
    #    [...] means one or more
    #    Foo   is a reference to a production by that name.
    #    "foo" matches the literal text "foo".

    def split_at(ls, splitter):
        start = 0
        items = []
        n = len(ls)
        for i in xrange(1, n):
            item = splitter(ls, start, i)
            if item is not None:
                items.append(item)
                start = i
        item = splitter(ls, start, n)
        if item is not None:
            items.append(item)
        return items

    all_standard_lits = set()

    def make_ptree(is_standard, toks):
        """
        Matches bracket operators to group the right-hand-side of a
        grammar production variant into a recursive-expression form.

        is_standard : True iff this is part of the Java grammar proper.
               Not a non-standard extension like the template grammar.
        """
        def make_node(toks, i):
            tok = toks[i]
            classification = _classify_token(tok)
            if classification == _TOK_STRING:
                if is_standard:
                    all_standard_lits.add(tok[0])
                return ({ 'name': 'lit', 'pleaf': tok }, i + 1)
            if classification == _TOK_IDENT:
                return ({ 'name': 'ref', 'pleaf': tok }, i + 1)
            if tok[0] == '!':
                if i + 1 == len(toks):
                    raise SyntaxError('Expected content after "!"')
                sub_node, j = make_node(toks, i + 1)
                if sub_node is None:
                    raise SyntaxError('Expected content after "!" not annotation')
                return ({ 'name': 'nla', 'ptree': [sub_node] }), j
            if tok[0] in ('(', '[', '{'):
                end_bracket = _BRACKET_OTHERS[tok[0]]
                j, n = i + 1, len(toks)
                ptree = []
                while True:
                    if j == n:
                        raise SyntaxError(repr(toks))
                    if has_text(toks[j], end_bracket):
                        return (
                            {
                                'name': '%s%s' % (tok[0], end_bracket),
                                'ptree': ptree
                            },
                            j + 1
                        )
                    else:
                        sub_node, j = make_node(toks, j)
                        if sub_node is not None:
                            ptree.append(sub_node)
                        else:
                            break
            if _is_annotation(tok[0]):
                return None, i
            raise SyntaxError(repr((tok, toks)))
        ptree = []
        i, n = 0, len(toks)
        while i < n:
            node, j = make_node(toks, i)
            if node is None:
                assert j == i
                break
            assert j > i
            ptree.append(node)
            i = j
        return ptree, i

    def line_of((_, pos)):
        ln, _, _ = pos
        return ln
    def variant_maker(prod_name, is_standard):
        names_used = set()
        def get_variant_name(toks, annots):
            name_from_annotation = None
            for annot in annots:
                if annot[0].startswith('(@name='):
                    name_from_annotation = annot[0][len('(@name='):-1]
                    break
            if name_from_annotation is not None:
                name = name_from_annotation
            else:
                base_name_parts = []
                for tok in toks:
                    if _classify_token(tok) == _TOK_STRING:
                        str_content = tok[0][1:-1]
                        str_index = 0
                        while str_index < len(str_content):
                            alnum = _to_alnum(str_content, str_index)
                            if alnum is None:
                                str_index += 1
                            else:
                                alnum_part, str_index = alnum
                                base_name_parts.append(alnum_part)
                    elif has_text(tok, '!'):
                        if base_name_parts and base_name_parts[-1] == 'not':
                            base_name_parts[-1] = 'exp'
                        else:
                            base_name_parts.append('not')
                    elif _is_annotation(tok[0]):
                        break
                    else:
                        base_name_parts.append(_NON_IDENT_CHAR.sub('', tok[0]))
                base_name_parts = [x for x in base_name_parts if x]
                # Now we have parts that contain the names of child productions
                # and descriptions of literal text.
                # Normalize underscores and turn it into one nice big
                # UpperCamelCase identifier.
                base_name = _UNDERSCORE_NEEDED.sub('_', '_'.join(base_name_parts))
                base_name = _underscores_to_upper_camel(base_name or 'epsilon')
                counter = 0
                name = base_name
            while name in names_used:
                counter += 1
                name = '%s$%d' % (base_name, counter)
            names_used.add(name)
            if name_from_annotation is not None and name != name_from_annotation:
                raise Exception('name from annotations %r is %s but needed disambiguation from %r'
                                % (annots, name_from_annotation, names_used))
            return name
        def maybe_make_variant(toks, s, e):
            if e == len(toks) or line_of(toks[s]) != line_of(toks[e]):
                sub_toks = toks[s:e]
                ptree, index_after = make_ptree(is_standard, sub_toks)
                annots = sub_toks[index_after:]
                assert all(_is_annotation(annot_text) for annot_text, annot_pos in annots), repr(annots)
                name = get_variant_name(sub_toks, annots)
                return {
                    'name': name,
                    'ptree': ptree,
                    'annots': annots,
                    }
            return None

        return maybe_make_variant

    def get_prod_name(toks, i):
        if (i + 1 < len(toks)
            and starts_line(toks[i])
            and is_ident(toks[i])
            and has_text(toks[i+1], ':')):
            return toks[i][0]
    def prod_maker(chapter_name):
        def maybe_make_prod(toks, s, e):
            if e > s:
                if e == len(toks) or get_prod_name(toks, e) is not None:
                    name = get_prod_name(toks, s)
                    if name is None:
                        ln, co, ch = toks[s][1]
                        print 'No name at %d:%d' % (ln, co)
                    sub_toks = name is not None and toks[s + 2:e] or toks[s:e]
                    annots = ()
                    if name is not None:
                        name_tok = toks[s]
                        for i in xrange(0, len(sub_toks)):
                            if not same_line(name_tok, sub_toks[i]):
                                annots = tuple(sub_toks[:i])
                                sub_toks = sub_toks[i:]
                                break
                    is_standard = all(x[0] != '@nonstandard' for x in annots)
                    return {
                        'name': name or 'Unknown',
                        'toks': sub_toks,
                        'variants': split_at(sub_toks, variant_maker(name, is_standard)),
                        'annots': annots,
                    }
            return None
        return maybe_make_prod

    def get_chapter_name(toks, i):
        if (i + 4 < len(toks)
            and starts_line(toks[i]) and has_text(toks[i], '(')
            and has_text(toks[i+1], 'chapter')
            and has_text(toks[i+2], '=')
            and is_ident(toks[i+3])
            and has_text(toks[i+4], ')')):
            return toks[i+3][0]
        return None
    def maybe_make_chapter(toks, s, e):
        if e > s:
            if e == len(toks) or get_chapter_name(toks, e) is not None:
                name = get_chapter_name(toks, s)
                sub_toks = name is not None and toks[s + 5:e] or toks[s:e]
                return {
                    'name': name or 'Unknown',
                    'toks': sub_toks,
                    'prods': split_at(sub_toks, prod_maker(name))
                    }
        return None

    grammar = split_at(filtered_tokens, maybe_make_chapter)

    if verbose:
        import pprint
        print '\nGRAMMAR'
        for chapter in grammar:
            print '\tChapter %s' % chapter['name']
            for prod in chapter['prods']:
                print '\t\tProd %s' % prod['name']
                for variant in prod['variants']:
                    print '\t\t\tVariant %s' % variant['name']
                    pprinter = pprint.PrettyPrinter(indent=2, width=68)
                    pprinted_variant = pprinter.pformat(variant['ptree'])\
                                               .replace('\n', '\n\t\t\t\t')
                    print '\t\t\t\t%s' % pprinted_variant

    def for_each_chapter(f):
        for chapter in grammar:
            f(chapter)

    def for_each_prod(f):
        def g(chapter):
            for prod in chapter['prods']:
                f(chapter, prod)
        for_each_chapter(g)

    def for_each_variant(f):
        def g(chapter, prod):
            for variant in prod['variants']:
                f(chapter, prod, variant)
        for_each_prod(g)

    def assignforlambda(obj, key, val):
        obj[key] = val

    prods_by_name = {}
    for_each_prod(lambda c, p: assignforlambda(prods_by_name, p['name'], p))

    def compute_reachable():
        reachable = set()
        def compute_reachable_from(pn):
            def walk_ptree(pt):
                if 'ptree' in pt:
                    for sub in pt['ptree']:
                        walk_ptree(sub)
                elif pt['name'] == 'ref':
                    ref_name = pt['pleaf'][0]
                    compute_reachable_from(ref_name)
            if pn not in reachable:
                reachable.add(pn)
                prod = prods_by_name.get(pn, None)
                if prod is not None:
                    for v in prod['variants']:
                        for pt in v['ptree']:
                            walk_ptree(pt)
        for pn, prod in prods_by_name.iteritems():
            if '@toplevel' in (annot[0] for annot in prod['annots']):
                compute_reachable_from(pn)
        return reachable

    reachable = compute_reachable()

    def prune_unreachable():
        unreachable = set(prods_by_name.keys()).difference(reachable)
        if verbose:
            # Dump a list of productions that are not reachable from @toplevel prods
            for ur in unreachable:
                print 'UNREACHABLE: %s' % ur
        for ur in unreachable:
            del prods_by_name[ur]
        def prune(c):
            c['prods'] = tuple([p for p in c['prods'] if p['name'] in reachable])
        for_each_chapter(prune)
    prune_unreachable()

    def create_enum_members():
        enum_members = []
        for chapter in grammar:
            base_type = 'Base%sNode.class' % chapter['name']
            for prod in chapter['prods']:
                enum_members.append(
                    '''
  /**
   * <pre>%(jsdoc)s</pre>
   */
  %(name)s(%(base_type)s, %(name)sNode.Variant.class),
''' % {
    'jsdoc': _jsdoc_of(_tokens_to_text(prod['toks']), prefix = '   * '),
    'name': prod['name'],
    'name_str': _java_str_lit(prod['name']),
    'base_type': base_type,
    })
        return enum_members

    # Produce an enum with an entry for each production.
    emit_java_file(
        'NodeType',

        '''
package %(package)s;

import com.mikesamuel.cil.parser.ParSer;
import com.mikesamuel.cil.parser.ParSerable;
import com.mikesamuel.cil.ptree.PTree;

/**
 * A Java language production.
 */
@javax.annotation.Generated(%(generator)s)
public enum NodeType implements ParSerable {
%(members)s
  ;

  private final Class<? extends BaseNode> nodeBaseType;
  private final Class<? extends Enum<? extends NodeVariant>> variantType;
  private final ParSerable parSerable;

  NodeType(
      Class<? extends BaseNode> nodeBaseType,
      Class<? extends Enum<? extends NodeVariant>> variantType) {
    this.nodeBaseType = nodeBaseType;
    this.variantType = variantType;
    this.parSerable = PTree.nodeWrapper(this);
  }

  /**
   * The base type for this node.
   * This establishes where in the gross structure of a compilation unit
   * nodes of this kind can appear.
   */
  public Class<? extends BaseNode> getNodeBaseType() {
    return nodeBaseType;
  }
  /**
   * Type that allows enumeration over the syntactic variants.
   */
  public Class<? extends Enum<? extends NodeVariant>> getVariantType() {
    return variantType;
   }
  /**
   * A parser/serializer instance that operates on nodes of this kind.
   */
  @Override
  public ParSer getParSer() {
    return parSerable.getParSer();
  }
}
        ''' % {
            'package': _JAVA_PACKAGE,
            'generator': generator,
            'members': ''.join(create_enum_members()),
        })

    variant_lookaheads = {}
    def compute_lookaheads():
        la_toks_all = {}
        la_refs_all = {}

        regex_esc = {
            '[':  r'\[',
            ']':  r'\]',
            '(':  r'\(',
            ')':  r'\)',
            '{':  r'\{',
            '}':  r'\}',
            '+':  r'\+',
            '-':  r'\-',
            '*':  r'\*',
            '?':  r'\?',
            '\\': r'\\',
            '.':  r'\.',
            '^':  r'\^',
            '$':  r'\$',
            '|':  r'\|',
            }

        # Compute lookahead by following all paths from each variant
        # and returning pairs of the form
        # (atomic_ptrees_la, whether_empty can be matched)
        def compute_lookahead(c, p, v):
            pn = p['name']
            vn = v['name']
            la_toks = set()
            la_refs = set()

            def compute_la_ptree(pt):
                """
                True if branch definitely matches a token
                """
                name = pt['name']
                if 'ptree' in pt:
                    sub_matches = False
                    for sub in pt['ptree']:
                        if compute_la_ptree(sub):
                           sub_matches = True
                           break
                    return name in ('()') and sub_matches
                elif name == 'lit':
                    first_char = pt['pleaf'][0][1]  # Ignore double quotes
                    la_toks.add(regex_esc.get(first_char, first_char))
                    return True
                elif name == 'ref':
                    la_refs.add(pt['pleaf'][0])
                    # Assumes that only CompilationUnit matches the empty
                    # string and it is not used as a right-hand-side nonterminal
                    return True
                else:
                    raise Exception(name)

            matches_token = compute_la_ptree(
                { 'name': '()', 'ptree': v['ptree'] })

            # Check assumption above.
            assert matches_token or pn == 'CompilationUnit', pn

            la_toks_all[(pn, vn)] = la_toks
            la_refs_all[(pn, vn)] = la_refs

        for_each_variant(compute_lookahead)
        builtin = {
            'Identifier': (_JAVA_IDENT_START_CHARSET,),
            'IdentifierChars': (_JAVA_IDENT_START_CHARSET,),
            'FloatingPointLiteral': (r'\.', '0-9',),
            'IntegerLiteral': (r'0-9',),
            'CharacterLiteral': (r"'",),
            'StringLiteral': (r'"',),
            # Comments are ignorable tokens that we lookback to capture,
            # so have no effect on lookahead.
            'JavaDocComment': (),
            }

        known = {}

        # Make sure lookahead includes '<' for productions annotated with
        # '@interp'.
        def lookahead_to_template(c, p):
            if any(x[0].startswith('@interp=') for x in p['annots']):
                known[p['name']] = set(['<'])
        for_each_prod(lookahead_to_template)

        if verbose:
            print "la_toks_all"
            for (pn, vn), v in la_toks_all.iteritems():
                print '\t%s.%s: %r' % (pn, vn, v)
            print "\nla_refs_all"
            for (pn, vn), v in la_refs_all.iteritems():
                print '\t%s.%s: %r' % (pn, vn, v)

        # Iterate until convergence which handles LR cycles.
        not_expanded_count = 0
        was_expanded = [False]
        while not_expanded_count < 2:
            was_expanded[0] = False

            def expand(c, p):
                pn = p['name']
                if pn not in known:
                    known[pn] = set()
                known_for_p = known[pn]
                size_before = len(known_for_p)
                for v in p['variants']:
                    vn = v['name']
                    toks_for_v = la_toks_all[(pn, vn)]
                    for referent in la_refs_all[(pn, vn)]:
                        if referent in builtin:
                            toks_for_v.update(builtin[referent])
                        elif referent in known:
                            toks_for_v.update(known[referent])
                        elif referent == 'builtin':
                            toks_for_v.update(builtin[pn])
                    known_for_p.update(toks_for_v)
                if len(known_for_p) > size_before:
                    was_expanded[0] = True

            for_each_prod(expand)

            if not was_expanded[0]:
                not_expanded_count += 1

        letters = set(c for c in 'abcdefghijklmnopqrstuvwxyz')
        for k, la in la_toks_all.items():
            if _JAVA_IDENT_START_CHARSET in la:
                la.difference_update(letters)

        return la_toks_all

    variant_lookaheads = compute_lookaheads()
    if verbose:
        for ((pn, vn), toks) in variant_lookaheads.iteritems():
            print 'LA %s.%s = %r' % (pn, vn, toks)

    # We need to know which productions can match the empty string so we
    # can reliably identify calls which might be left-recursive.
    # We follow each possible branch from a production to see whether it is
    # epsilon-matching.  We could eliminate cyclic branches if we knew whether
    # a branch was left-recursive, but we don't know that yet.
    def compute_empty_matching():
        # We define a production as empty-matching if any variant of it is
        # empty-matching.

        # We keep track of a contingency graph from variants to productions.
        # For each variant, it is either not empty matching since it
        # requires a literal match, or it is empty matching only when all of
        # a set of non-optional dependencies are empty-matching.
        computed = set()
        empty_matching = set()

        computed.add('builtin')

        EMPTY_PATH = True
        NON_EMPTY_PATH = False
        CYCLIC_PATH = None

        # We try to detect cycles to determine whether to cache.
        CYCLE_DETECTED = True
        NO_CYCLE_DETECTED = False

        def walk_optional(pt, seen):
            # Do nothing since there is always an epsilon branch through
            # an option.
            return (EMPTY_PATH, NO_CYCLE_DETECTED)

        def walk_lit(pt, seen):
            return (pt['pleaf'][0] == '', NO_CYCLE_DETECTED)

        def walk_ref(pt, seen):
            target = pt['pleaf'][0]
            if target in computed:
                return (target in empty_matching, NO_CYCLE_DETECTED)
            if target in seen:
                return (CYCLIC_PATH, CYCLE_DETECTED)
            seen.add(target)
            result = walk_prod(prods_by_name[target], seen, False)
            seen.remove(target)
            return result

        def walk_cat(pt, seen):
            all_empty_or_cyclic = EMPTY_PATH  # True for empty concat
            cycle_detected = NO_CYCLE_DETECTED
            for cpt in pt['ptree']:
                e, c = walk_pt(cpt, seen)
                if e == NON_EMPTY_PATH:
                    all_empty_or_cyclic = NON_EMPTY_PATH
                    break
                if c == CYCLE_DETECTED:
                    cycle_detected = CYCLE_DETECTED
            return (all_empty_or_cyclic, cycle_detected)

        pt_handlers = {
            'ref': walk_ref,
            'lit': walk_lit,
            '()': walk_cat,
            '[]': walk_optional,
            # Treat Kleene star as optional Kleene plus
            '{}': walk_optional,
            # Lookahead consumes no input.
            'nla': walk_optional,
        }

        def walk_pt(pt, seen):
            return pt_handlers[pt['name']](pt, seen)

        def walk_prod(p, seen, topmost):
            pn = p['name']
            # A disjunction of zero options fails to match any string,
            # so it is safe to start with not-empty.
            overall_empty = None
            cycle = NO_CYCLE_DETECTED
            for v in p['variants']:
                e, c = walk_pt(
                    { 'name': '()', 'ptree': v['ptree'] },
                    seen)
                if c == CYCLE_DETECTED:
                    cycle = CYCLE_DETECTED
                if e == EMPTY_PATH:
                    overall_empty = True
                    break
                elif e == NON_EMPTY_PATH:
                    overall_empty = False
            if topmost or cycle == NO_CYCLE_DETECTED:
                computed.add(pn)
                if overall_empty:
                    empty_matching.add(pn)
            return (overall_empty, cycle)

        def start_walk(_, p):
            pn = p['name']
            if pn in computed:
                return
            seen = set()
            seen.add(pn)
            walk_prod(p, seen, True)

        for_each_prod(start_walk)
        return empty_matching

    empty_matching = compute_empty_matching()

    if verbose:
        #not_empty_matching_sorted = [pn for pn in prods_by_name.iterkeys()
        #                             if pn not in empty_matching]
        #not_empty_matching_sorted.sort()
        #print 'NOT EMPTY\n\t%s' % ('\n\t'.join(not_empty_matching_sorted))
        empty_matching_sorted = [pn for pn in prods_by_name.iterkeys()
                                 if pn in empty_matching]
        empty_matching_sorted.sort()
        print 'EMPTY MATCHING\n\t%s' % ('\n\t'.join(empty_matching_sorted))


    # Figure out what is left-recursive and compute a table mapping
    # (prod, variant) to the shortest LR (prod, variant) loop.
    left_recursion = {}
    for_each_prod(
        lambda c, p: assignforlambda(left_recursion, p['name'], {}))
    left_calls_per_variant = {}

    def left_call_names_of(p, v):
        pn = p['name']
        vn = v['name']
        key = (pn, vn)
        left_calls = left_calls_per_variant.get(key)
        if left_calls is not None:
            return left_calls
        left_calls = []
        def add_left_calls(pts):
            for pt in pts:
                ptn = pt['name']
                if ptn in ('{}', '[]', '()'):
                    add_left_calls(pt['ptree'])
                elif ptn == 'ref':
                    name = pt['pleaf'][0]
                    if name == 'builtin':
                        break
                    left_calls.append(name)
                    if name not in empty_matching:
                        break
                    elif ptn == 'nla':
                        continue
                else:
                    break
        add_left_calls(v['ptree'])
        if verbose and len(left_calls) >= 2:
            print 'MULTIPLE LEFT CALLS IN %s: %r' % (v['name'], left_calls)
        left_calls_per_variant[key] = tuple(left_calls)
        return left_calls

    def find_left_recursion(chapter, start_prod, start_variant):
        start_prod_name = start_prod['name']
        start_variant_name = start_variant['name']
        if start_variant_name in left_recursion[start_prod_name]:
            return
        seen = set()
        # Track the chain of calls that reach left-recursively
        variant_chain = []
        def find_left_calls(p, v):
            pn = p['name']
            vn = v['name']
            variant_chain.append((pn, vn))
            try:
                for left_call_name in left_call_names_of(p, v):
                    if left_call_name == start_prod_name:
                        if verbose:
                            print 'LR %s::%s via\n\t%s' % (
                                start_prod_name, start_variant_name,
                                '\n\t'.join(repr(x) for x in variant_chain))
                        left_recursion[start_prod_name][start_variant_name] = (
                            tuple(variant_chain))
                        return True
                    if left_call_name in seen:
                        return False
                    seen.add(left_call_name)
                    callee_p = prods_by_name[left_call_name]
                    for callee_v in callee_p['variants']:
                        if find_left_calls(callee_p, callee_v):
                            return True
            finally:
                variant_chain.pop()
            return False
        find_left_calls(start_prod, start_variant)
    for_each_variant(find_left_recursion)

    if verbose:
        print '\nLEFT_CALLS_PER_VARIANT'
        for ((pn, vn), lcs) in left_calls_per_variant.iteritems():
            if vn in left_recursion[pn]:
                print '\t%s.%s -> %r' % (pn, vn, lcs)
        print

    if dot_out is not None:
        def write_dot():
            prod_name_to_referents = {}
            def is_left(src, dest_name):
                src_name = src['name']
                for variant in src['variants']:
                    if dest_name in left_calls_per_variant[
                            (src_name, variant['name'])]:
                        return True
                return False
            def walk_variant(c, p, v):
                pn = p['name']
                if pn not in prod_name_to_referents:
                    prod_name_to_referents[pn] = {}
                def walk(pt):
                    if 'ptree' in pt:
                        for spt in pt['ptree']:
                            walk(spt)
                    elif 'ref' == pt['name']:
                        callee_name = pt['pleaf'][0]
                        prod_name_to_referents[pn][callee_name] = (
                            prod_name_to_referents[pn].get(callee_name, False)
                            or is_left(p, callee_name))
                walk({ 'name': '()', 'ptree': v['ptree'] })
            for_each_variant(walk_variant)
            with open(dot_out, 'w') as dot_out_file:
                print >>dot_out_file, 'digraph nonterminals {'
                for pn, referent_to_is_left in (
                        prod_name_to_referents.iteritems()):
                    print >>dot_out_file, '  %s;' % pn
                    for rn, is_left in referent_to_is_left.iteritems():
                        print >>dot_out_file, '  %s -> %s [color=%s];' % (
                            pn, rn, is_left and 'blue' or 'black')
                print >>dot_out_file, '}'
        write_dot()

    def ptree_to_java_builder(prod, pt, prefix):
        name = pt['name']
        prefix_plus = '    %s' % prefix
        extra_imports = set()
        if 'ptree' in pt:
            calls = []
            for subtree in pt['ptree']:
                sub_bldr, ei = ptree_to_java_builder(
                    prod, subtree, prefix=prefix_plus)
                extra_imports.update(ei)
                calls.append('%s.add(%s)' % (prefix_plus, sub_bldr))
            sub = '\n'.join(calls)
        else:
            (leaf_text, (leaf_ln, leaf_co, leaf_ci)) = pt['pleaf']
            if name == 'ref':
                if leaf_text == 'builtin':
                    sub = '%s.add(Tokens.%s)' % (
                        prefix_plus,
                        _camel_to_underscores(prod['name']))
                    leaf_value = 'Tokens.%sParSec' % name
                    extra_imports.add('com.mikesamuel.cil.ptree.Tokens')
                else:
                    sub = '%s.add(NodeType.%s)' % (prefix_plus, leaf_text)
            else:  # 'lit'
                leaf_value = _java_str_lit(leaf_text[1:-1])
                optional_token_merge_hazard = ''
                if (leaf_value == '">"'
                    and prod['name'] in ('TypeArguments', 'TypeParameters')):
                    optional_token_merge_hazard = ', true'
                sub = '%s.leaf(%s%s, %d, %d, %d)' % (
                    prefix_plus,
                    leaf_value, optional_token_merge_hazard,
                    leaf_ln, leaf_co, leaf_ci)

        return (
            (
                ''.join([
                    '',
                    '\n%(pp)sPTree.builder(%(kind)s)',
                    '\n%(sub)s',
                    '\n%(pp)s.build()'])
                % {
                    'kind': _PTREE_NAME_TO_KIND[name],
                    'pp': prefix_plus,
                    'sub': sub,
                }
            ), extra_imports
        )

    # For each production, produce a Node subclass.
    def write_node_class_for_production(chapter, prod):
        node_class_name = '%sNode' % prod['name']
        if source_file_exists('%s.java' % node_class_name):
            return
        variants = prod['variants']

        extra_imports = set((
            'com.mikesamuel.cil.parser.Lookahead1',
            'com.mikesamuel.cil.parser.ParSer',
            'com.mikesamuel.cil.parser.ParSerable',
            'com.mikesamuel.cil.parser.SourcePosition',
            'com.mikesamuel.cil.ptree.PTree',
        ))

        is_inner_node = not (len(variants) == 1 and variants[0]['name'] == 'Builtin')

        # extra code for the Node body.
        extra_code = []
        custom_code, custom_code_imports = _CUSTOM_NODE_CONTENT.get(prod['name'], ('', ()))
        extra_code.append(custom_code)
        extra_imports.update(custom_code_imports)

        builder_rtype = '%s.Builder' % node_class_name

        # extra code for the custom builder body.
        builder_code = []

        # extra code for the custom builder's copyMetadataFrom method
        builder_copy_code = []

        # extra code for the custom builder's build method
        builder_build_code = []

        if is_inner_node:
            ctor_formals = 'Iterable<? extends BaseNode> children'
            builder_kind = 'Inner'
            builder_actuals = 'getChildren()'
            super_ctor_actuals = 'children, null'
            builder_code.append('''
    @Override public %(builder_rtype)s add(BaseNode child) {
      super.add(child);
      return this;
    }
    @Override public %(builder_rtype)s add(int index, BaseNode child) {
      super.add(index, child);
      return this;
    }
    @Override public %(builder_rtype)s replace(int index, BaseNode child) {
      super.replace(index, child);
      return this;
    }
    @Override public %(builder_rtype)s remove(int index) {
      super.remove(index);
      return this;
    }
''' % { 'builder_rtype': builder_rtype })
        else:
            ctor_formals = 'String literalValue'
            builder_kind = 'Leaf'
            builder_actuals = 'getLiteralValue()'
            super_ctor_actuals = 'ImmutableList.of(), literalValue'
            extra_imports.add('com.google.common.collect.ImmutableList')
            builder_code.append('''
    @Override public %(builder_rtype)s leaf(String newValue) {
      super.leaf(newValue);
      return this;
    }
''' % { 'builder_rtype': builder_rtype })

        def create_variant_members():
            variant_code = []
            lr_prod = left_recursion[prod['name']]
            is_lr_forwarding = True
            prod_matches_empty = prod['name'] in empty_matching
            annot_converters = {
                'name': None,
                'postcond': (
                    'Predicate<SList<Event>>',
                    ('com.mikesamuel.cil.event.Event',
                     'com.mikesamuel.cil.parser.SList',
                     'com.google.common.base.Predicate'),
                    lambda x: x
                ),
            }

            # Treat LR productions that just forward to other
            # productions, so have no seed of their own as not
            # really LR.
            for v in variants:
                if v['name'] in lr_prod:
                    vptree = v['ptree']
                    if len(vptree) == 1 and vptree[0]['name'] == 'ref':
                        alias_name = vptree[0]['pleaf'][0]
                        alias = prods_by_name[alias_name]
                        lr_alias = left_recursion[alias_name]
                        if any(av['name'] in lr_alias
                               for av in alias['variants']):
                            continue
                is_lr_forwarding = False
                break
            if is_lr_forwarding and verbose:
                print 'LR FORWARDING: %s' % prod['name']
            for v in variants:
                ptree_builder, v_extra_imports = ptree_to_java_builder(
                    prod,
                    { 'name': None, 'ptree': v['ptree'] },
                    prefix='    ')
                extra_imports.update(v_extra_imports)
                is_lr = v['name'] in lr_prod and not is_lr_forwarding
                if is_lr and verbose:
                    print 'LR: %s::%s' % (prod['name'], v['name'])
                if (prod_matches_empty
                    # JavaDocComment matches ignorable tokens so does not
                    # participate in Lookahead
                    or prod['name'] == 'JavaDocComment'):
                    la = 'null'
                else:
                    la = 'Lookahead1.of(%s)' % (', '.join([
                        _java_str_lit(la_char) for la_char
                        in sorted(variant_lookaheads[
                            (prod['name'], v['name'])])]))
                overridden_methods = []
                for (annot_text, _) in v['annots']:
                    if annot_text.startswith('(@name='): continue
                    annot_value, annot_name = None, None
                    override = None
                    if annot_text.startswith('('):
                        annot_name, annot_value = annot_text[2:-1].split('=')
                        annot_converter = annot_converters[annot_name]
                        if annot_converter is not None:
                            annot_type, annot_extra_imports, annot_value_conv = annot_converter
                            override = {
                                'type': annot_type,
                                'name': 'get%s%s' % (annot_name[0].upper(), annot_name[1:]),
                                'value': annot_value_conv(annot_value),
                            }
                            extra_imports.update(annot_extra_imports)
                    else:
                        annot_name = annot_text[1:]
                        override = {
                            'type': 'boolean',
                            'name': 'is%s%s' % (annot_name[0].upper(), annot_name[1:]),
                            'value': 'true'
                            }
                    if override is not None:
                        overridden_methods.append(
                            (
                                '      @Override\n'
                                '      public %(type)s %(name)s() {\n'
                                '        return %(value)s;\n'
                                '      }\n'
                            ) % override)

                variant_code.append(
                    (
                        '    /** */\n'
                        '    %(variant_name)s(%(ptree)s, %(is_lr)s, %(la)s)%(overrides)s,'
                    ) % {
                        'variant_name': v['name'],
                        'ptree': ptree_builder,
                        'is_lr': is_lr and 'true' or 'false',
                        'la': la,
                        'overrides': (
                            overridden_methods
                            and '{\n%s    }' % '\n'.join(overridden_methods) or ''),
                    })
            return ('\n'.join(variant_code))

        variant_members = create_variant_members()

        traits = []
        for (annot_name, _) in prod['annots']:
            if annot_name.startswith('(@trait='):
                traits.extend([x.strip() for x in annot_name[8:-1].split(',') if len(x.strip())])

        for trait in traits:
            extra_imports.add('%s.traits.%s' % (_JAVA_PACKAGE, trait))
            trait_fields, trait_imports = _TRAITS.get(trait, ((), ()))
            extra_imports.update(trait_imports)
            for trait_type, trait_field in trait_fields:
                record = {
                    'trait_field': trait_field,
                    'utrait_field': '%s%s' % (trait_field[0].upper(), trait_field[1:]),
                    'trait_type': trait_type,
                    'builder_rtype': builder_rtype,
                }
                extra_code.append(
                    ('  private %(trait_type)s %(trait_field)s;\n'
                     '\n'
                     '  @Override\n'
                     '  public final void set%(utrait_field)s(%(trait_type)s new%(utrait_field)s) {\n'
                     '    this.%(trait_field)s = new%(utrait_field)s;\n'
                     '  }\n'
                     '\n'
                     '  @Override\n'
                     '  public final %(trait_type)s get%(utrait_field)s() {\n'
                     '    return this.%(trait_field)s;\n'
                     '  }\n')
                    % record)
                builder_copy_code.append(
                    ('      set%(utrait_field)s(source.get%(utrait_field)s());')
                    % record
                    )
                builder_build_code.append(
                    ('      newNode.set%(utrait_field)s(this.%(trait_field)s);')
                    % record
                    )
                builder_code.append(
                    ('    private %(trait_type)s %(trait_field)s;\n'
                     '\n'
                     '    /** Sets metadata for new instance. */\n'
                     '    public final %(builder_rtype)s\n'
                     '    set%(utrait_field)s(%(trait_type)s new%(utrait_field)s) {\n'
                     '      if (!Objects.equal(this.%(trait_field)s, new%(utrait_field)s)) {\n'
                     '        this.%(trait_field)s = new%(utrait_field)s;\n'
                     '        markChanged();\n'
                     '      }\n'
                     '      return this;\n'
                     '    }\n'
                     '\n'
                     '    final %(trait_type)s get%(utrait_field)s() { return %(trait_field)s; }\n'
                    ) % record)
                extra_imports.add('com.google.common.base.Objects')
        trait_ifaces = ''
        if traits:
            trait_ifaces = '\nimplements %s' % (', '.join(traits))

        extra_import_stmts = ''.join(
            ['import %s;\n' % imp for imp in sorted(extra_imports)])

        emit_java_file(
            node_class_name,
            '''
package %(package)s;

%(extra_import_stmts)s
/**
 * Node for the JLS %(name)s production.
 *
 * <pre>
 * %(grammar_jsdoc)s
 * </pre>
 */
@javax.annotation.Generated(%(generator)s)
public final class %(node_class_name)s extends %(base_node_class)s%(trait_ifaces)s {

  private %(node_class_name)s(
      Variant v, %(ctor_formals)s) {
    super(v, %(super_ctor_actuals)s);
  }

%(extra_code)s

  @Override
  public Variant getVariant() {
    return (Variant) super.getVariant();
  }

  @Override
  public Builder builder() {
    @SuppressWarnings("synthetic-access")
    Builder b = new Builder(this);
    return b;
  }

  /** Variants of this node. */
  public enum Variant implements NodeVariant {
%(variant_members)s
    ;

    private final ParSerable parSerable;
    private final boolean isLeftRecursive;
    private final Lookahead1 lookahead1;

    Variant(ParSerable parSerable, boolean isLR, Lookahead1 lookahead1) {
      this.parSerable = parSerable;
      this.isLeftRecursive = isLR;
      this.lookahead1 = lookahead1;
    }

    @Override
    public ParSer getParSer() { return parSerable.getParSer(); }

    @Override
    public NodeType getNodeType() { return NodeType.%(name)s; }

    @Override
    public boolean isLeftRecursive() { return isLeftRecursive; }

    @Override
    public Lookahead1 getLookahead1() { return lookahead1; }

    @Override
    public %(node_class_name)s.Builder nodeBuilder() {
      @SuppressWarnings("synthetic-access")
      %(node_class_name)s.Builder b = new %(node_class_name)s.Builder(this);
      return b;
    }

    @Override
    public String toString() {
      return getNodeType().name() + "." + name();
    }
  }

  /** A builder for %(node_class_name)ss */
  public static final class Builder
  extends BaseNode.%(builder_kind)sBuilder<%(node_class_name)s, Variant> {
    private Builder(Variant v) {
      super(v);
    }

    private Builder(%(node_class_name)s source) {
      super(source);
    }

    private Builder(%(node_class_name)s.Builder source) {
      super(source);
    }

    @Override
    public Builder builder() {
      return new Builder(this);
    }

%(builder_code)s

    @Override
    public Builder copyMetadataFrom(%(node_class_name)s source) {
%(builder_copy_code)s
      super.copyMetadataFrom(source);
      return this;
    }

    @Override
    public Builder copyMetadataFrom(BaseNode.Builder<%(node_class_name)s, Variant> src) {
      Builder source = (Builder) src;
%(builder_copy_code)s
      super.copyMetadataFrom(source);
      return this;
    }

    @Override
    @SuppressWarnings("synthetic-access")
    public %(node_class_name)s build() {
      %(node_class_name)s newNode = new %(node_class_name)s(
          getVariant(), %(builder_actuals)s);
%(builder_build_code)s
      SourcePosition sourcePosition = getSourcePosition();
      if (sourcePosition != null) {
        newNode.setSourcePosition(sourcePosition);
      }
      return newNode;
    }
  }
}
''' % {
    'package': _JAVA_PACKAGE,
    'extra_import_stmts': extra_import_stmts,
    'generator': generator,
    'grammar_jsdoc': _jsdoc_of(_tokens_to_text(prod['toks'])),
    'node_class_name': node_class_name,
    'name': prod['name'],
    'base_node_class': 'Base%sNode' % chapter['name'],
    'variant_members': variant_members,
    'ctor_formals': ctor_formals,
    'super_ctor_actuals': super_ctor_actuals,
    'builder_kind': builder_kind,
    'builder_actuals': builder_actuals,
    'extra_code': '\n'.join(extra_code),
    'builder_code': '\n'.join(builder_code),
    'builder_copy_code': '\n'.join(builder_copy_code),
    'builder_build_code': '\n'.join(builder_build_code),
    'trait_ifaces': trait_ifaces,
    })

    for_each_prod(write_node_class_for_production)

    def write_literal_tokens():
        keywords = []
        punctuation = []
        for lit in all_standard_lits:
            content = lit[1:-1]
            if 'a' <= content[0] and content[0] <= 'z':
                keywords.append(content)
            else:
                punctuation.append(content)
        keywords.sort()
        punctuation.sort()
        emit_java_file(
            'TokenStrings',
            '''
package %(package)s;

import com.google.common.collect.ImmutableSet;

/**
 * All the tokens that appear literally in the grammar.
 */
@javax.annotation.Generated(%(generator)s)
public final class TokenStrings {

  private TokenStrings() {
    // Provides static API
  }

  /**
   * Language keywords.
   * This includes literals like {@code false} that are not part of the
   * keyword list, but are instead reserved type names.
   */
  public static final ImmutableSet<String> RESERVED = ImmutableSet.copyOf(
      new String[] { %(reserved_words)s }
  );

  /**
   * Non-alphabetic strings that appear literally in the grammar.
   * This excludes strings that appear purely in the lexical grammar like
   * whitespace characters, comment boundaries, and string/char literal boundaries.
   */
  public static final ImmutableSet<String> PUNCTUATION = ImmutableSet.copyOf(
      new String[] { %(punctuation)s }
  );
}
''' % {
    'package': _JAVA_PACKAGE,
    'generator': generator,
    'reserved_words': ', '.join(_java_str_lit(s) for s in keywords),
    'punctuation': ', '.join(_java_str_lit(s) for s in punctuation),
})

    write_literal_tokens()

    def write_identifier_wrappers():
        wrappers = set(('Identifier',))
        def find_wrapper(c, p):
            variants = p['variants']
            if len(variants) == 1:
                v = variants[0]
                pt = v['ptree']
                if len(pt) == 1:
                    pt0 = pt[0]
                    if pt0['name'] == 'ref' and pt0['pleaf'][0] == 'Identifier':
                        wrappers.add(p['name'])

        for_each_prod(find_wrapper)

        emit_java_file(
            'IdentifierWrappers',
            '''
package %(package)s;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.EnumSet;

/**
 * The set of productions which just decorate {@link NodeType#Identifier}.
 */
@javax.annotation.Generated(%(generator)s)
public final class IdentifierWrappers {

  private IdentifierWrappers() {
    // Provides static API
  }

  // We key off the classes instead of the node types to avoid
  // class-initialization cycles.
  private static final ImmutableSet<NodeType> IDENT_WRAPPERS =
      Sets.immutableEnumSet(EnumSet.of(
%(wrappers)s));

  /**
   * True iff the given nodetype has a single variant that simply delegate to
   * Identifier.
   */
  public static boolean isIdentifierWrapper(NodeType nodeType) {
    return IDENT_WRAPPERS.contains(nodeType);
  }
}
''' % {
    'package': _JAVA_PACKAGE,
    'generator': generator,
    'wrappers': ',\n'.join('          NodeType.%s' % wrapper for wrapper in sorted(wrappers)),
})

    write_identifier_wrappers()

    def write_prod_annotations():
        """
        Variant annotations are stored with the variant via method overrides.
        Since there is one big enum for all node types, it's more efficient to
        store them in EnumMap tables instead of creating a class per node type.

        This is mostly for Template handling, which is a largely separable
        concern from the Java grammar.
        """
        tables = {}  # Maps annot name to prod_name to values

        value_types = {
            'InterpGroup': ('NodeType', ()),
            'Interp': ('LeftRecursion.Stage', ('com.mikesamuel.cil.parser.LeftRecursion',)),
            }

        def fill_tables(c, p):
            for annot_tok in p['annots']:
                (annot, (ln, co, ci)) = annot_tok
                if not _is_annotation(annot):
                    raise Exception('Bad annotation %r for %s' % (repr(annot), p['name']))

                if annot.startswith('(@'):
                    annot_name, value = annot[2:-1].split('=')
                else:
                    annot_name = annot[1:]
                    value = 'true'
                if annot_name in ('trait',):
                    continue
                annot_name = '%s%s' % (annot_name[0].upper(), annot_name[1:])
                table = tables.get(annot_name)
                if table is None:
                    table = {}
                    tables[annot_name] = table
                table[p['name']] = value
        for_each_prod(fill_tables)

        imports = set()
        table_defs = []
        for (table_name, prod_to_value) in tables.iteritems():
            annot_name = '%s%s' % (table_name[0].lower(), table_name[1:])
            is_set = tuple(set(prod_to_value.itervalues())) == ('true',)
            if is_set:
                imports.add('com.google.common.collect.ImmutableSet')
                imports.add('com.google.common.collect.Sets')
                imports.add('java.util.EnumSet')
                table_defs.append(
                    '''
  /** Productions annotated with <code>&#64;%(annot_name)s</code> */
  public static final ImmutableSet<NodeType> %(table_name_uc)s = Sets.immutableEnumSet(
      EnumSet.of(%(members)s));
''' % {
    'annot_name': annot_name,
    'table_name': table_name,
    'table_name_uc': _camel_to_underscores(table_name),
    'members': ', '.join(['NodeType.%s' % pn for pn in sorted(prod_to_value.keys())]),
    })
            else:
                imports.add('com.google.common.collect.ImmutableMap')
                imports.add('com.google.common.collect.Maps')
                imports.add('java.util.EnumMap')
                value_type, value_type_imports = value_types.get(table_name, (table_name, ()))
                imports.update(value_type_imports)
                puts = []
                for (key, value) in prod_to_value.iteritems():
                    puts.append(
                        '    m.put(NodeType.%(key)s, %(value_type)s.%(value)s);' %
                        {
                            'key': key,
                            'value': value,
                            'value_type': value_type,
                        })
                table_defs.append(
                    '''
  /** Maps productions based on <code>&#64;%(annot_name)s</code> annotations. */
  public static final ImmutableMap<NodeType, %(value_type)s> %(table_name_uc)s;
  static {
    EnumMap<NodeType, %(value_type)s> m = new EnumMap<>(NodeType.class);
%(puts)s
    %(table_name_uc)s = Maps.immutableEnumMap(m);
  }
''' % {
    'annot_name': annot_name,
    'table_name': table_name,
    'table_name_uc': _camel_to_underscores(table_name),
    'value_type': value_type,
    'puts': '\n'.join(puts),
    })


        import_stmts = '\n'.join(['import %s;' % cl for cl in sorted(imports)])

        if not table_defs:
            return

        emit_java_file(
            'NodeTypeTables',
            '''
package %(package)s;

%(import_stmts)s

/**
 * The set of productions which just decorate {@link NodeType#Identifier}.
 */
@javax.annotation.Generated(%(generator)s)
public final class NodeTypeTables {

%(table_defs)s

}
''' % {
    'package': _JAVA_PACKAGE,
    'generator': generator,
    'import_stmts': import_stmts,
    'table_defs': ''.join(table_defs),
})

    write_prod_annotations()

    def write_visitor_classes():
        def write_visitor(c):
            cn = c['name']
            class_name = '%sVisitor' % cn
            node_type = 'Base%sNode' % cn

            visit_methods = [
                ('  protected @Nullable T\n'
                 '  visit%(name)s(@Nullable T x, %(name)sNode node) {\n'
                 '    return visitDefault(x, node);\n'
                 '  }')
                % p for p in c['prods']]
            visit_cases = [
                ('      case %(name)s:\n'
                 '        return visit%(name)s(x, (%(name)sNode) node);')
                % p for p in c['prods']]

            emit_java_file(
                class_name,
            '''
package %(package)s;

import javax.annotation.Nullable;

/**
 * Allows taking some production-specific action for a %(class_name)s.
 * <p>
 * Unless overridden, each visit* method returns
 * {@link %(class_name)s#visitDefault}.
 */
@javax.annotation.Generated(%(generator)s)
public abstract class %(class_name)s<T> {

  /**
   * Dispatches node to the appropriate visit* method.
   */
  public @Nullable T visit(@Nullable T x, %(node_type)s node) {
    NodeType nt = node.getVariant().getNodeType();
    switch (nt) {
%(visit_cases)s
      default:
        throw new AssertionError(nt);
    }
  }

  /** Called by visit* methods that are not overridden to do otherwise. */
  protected abstract @Nullable T
  visitDefault(@Nullable T x, %(node_type)s node);

%(visit_methods)s
}
''' % {
    'package': _JAVA_PACKAGE,
    'generator': generator,
    'class_name': class_name,
    'node_type': node_type,
    'visit_methods': '\n\n'.join(visit_methods),
    'visit_cases': '\n'.join(visit_cases),
})
        for_each_chapter(write_visitor)

        visit_methods = [
            ('  protected @Nullable T\n'
             '  visit%(name)s(@Nullable T x, Base%(name)sNode node) {\n'
             '    return visitDefault(x, node);\n'
             '  }')
            % c
            for c in grammar]
        visit_ifs = [
            ('    if (node instanceof Base%(name)sNode) {\n'
             '      return visit%(name)s(x, (Base%(name)sNode) node);\n'
             '    }')
            % c
            for c in grammar]

        emit_java_file(
            'BaseNodeVisitor',
            '''
package %(package)s;

import javax.annotation.Nullable;

/**
 * Allows taking some production-specific action for a %(class_name)s.
 * <p>
 * Unless overridden, each visit* method returns
 * {@link %(class_name)s#visitDefault}.
 */
@javax.annotation.Generated(%(generator)s)
public abstract class %(class_name)s<T> {

  /**
   * Dispatches node to the appropriate visit* method.
   */
  public @Nullable T visit(@Nullable T x, %(node_type)s node) {
%(visit_ifs)s
    throw new AssertionError(node.getClass());
  }

  /** Called by visit* methods that are not overridden to do otherwise. */
  protected abstract @Nullable T
  visitDefault(@Nullable T x, %(node_type)s node);

%(visit_methods)s
}
''' % {
    'package': _JAVA_PACKAGE,
    'generator': generator,
    'class_name': 'BaseNodeVisitor',
    'node_type': 'BaseNode',
    'visit_methods': '\n\n'.join(visit_methods),
    'visit_ifs': '\n'.join(visit_ifs),
})

    write_visitor_classes()


    if verbose:
        # Dump the "Public API" of each chapter -- those productions that are
        # referenced from other chapters.  Does not include "CompilationUnit"
        def dump_cross_chapter_uses():
            prods_by_chapter = {}
            def bc(c, p):
                prods_by_chapter[p['name']] = c['name']
            for_each_prod(bc)

            callees_by_pn = {}
            def ptree_walker(c, caller):
                caller_pn = caller['name']
                callees = set()
                def walk_ptree(pt):
                    if 'pleaf' in pt:
                        if pt['name'] == 'ref':
                            callee_pn = pt['pleaf'][0]
                            callees.add(callee_pn)
                    else:
                        for child in pt['ptree']:
                            walk_ptree(child)
                for variant in caller['variants']:
                    walk_ptree({ 'name': '()', 'ptree': variant['ptree'] })
                callees_by_pn[caller_pn] = callees
            for_each_prod(ptree_walker)

            called_cross_chapter = {}
            for caller_pn, callees in callees_by_pn.iteritems():
                caller_cn = prods_by_chapter[caller_pn]
                for callee_pn in callees:
                    if callee_pn == 'builtin': continue
                    callee_cn = prods_by_chapter[callee_pn]
                    if caller_cn != callee_cn:
                        s = called_cross_chapter.get(callee_cn)
                        if s is None:
                            s = set()
                            called_cross_chapter[callee_cn] = s
                        s.add(callee_pn)

            for cn, api in sorted(called_cross_chapter.items()):
                print 'Chapter %s exposes [%s]' % (cn, ', '.join(sorted(api)))

        dump_cross_chapter_uses()


if __name__ == '__main__':
    import argparse
    import os
    import os.path

    argparser = argparse.ArgumentParser()
    argparser.add_argument(
        '--srcdir',
        help=(
            'The root of the java source directory, e.g. basedir/src/main/java'
        ))
    argparser.add_argument(
        '--outdir',
        help=(
            'The root of the generated source directory for outputs,'
            ' e.g. basedir/target/generated-sources/parsehelpers'
        ))
    argparser.add_argument(
        '--dotout',
        help=('Path to graphviz .dot file that receives the non-terminal graph'
        ))
    argparser.add_argument(
        '-v',
        help='verbose',
        action='store_true')
    argparser.add_argument(
        'grammar_file', help='File containing the gramamr')
    args = argparser.parse_args()

    package_path = _JAVA_PACKAGE.split('.')

    def source_file_exists(rel_path):
        return os.path.isfile(
            os.path.join(
                os.path.join(args.srcdir, *package_path),
                rel_path))

    def emit_java_file(class_name, java_source):
        java_package_out_dir = os.path.join(args.outdir, *package_path)
        out_file = os.path.join(java_package_out_dir, '%s.java' % class_name)
        if not os.path.isdir(java_package_out_dir):
            os.makedirs(java_package_out_dir)
        with open(out_file, 'w') as out:
            out.write(java_source)

    process_grammar(
        grammar_text=open(args.grammar_file, 'r').read(),
        source_file_exists=source_file_exists,
        emit_java_file=emit_java_file,
        dot_out=args.dotout,
        verbose=args.v)
