#
# Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

from typing import Iterable, Any

import sys
import os
import re
import pdb

import gdb
import gdb.types
import gdb.printing
import gdb.unwinder
from gdb.FrameDecorator import FrameDecorator

if sys.version_info.major < 3:
    pyversion = '.'.join(str(v) for v in sys.version_info[:3])
    message = (
            'Cannot load SubstrateVM debugging assistance for GDB from ' + os.path.basename(__file__)
            + ': it requires at least Python 3.x. You are running GDB with Python ' + pyversion
            + ' from ' + sys.executable + '.'
    )
    raise AssertionError(message)

if int(gdb.VERSION.split('.')[0]) < 13:
    message = (
            'Cannot load SubstrateVM debugging assistance for GDB from ' + os.path.basename(__file__)
            + ': it requires at least GDB 13.x. You are running GDB ' + gdb.VERSION + '.'
    )
    raise AssertionError(message)


def trace(msg: str) -> None:
    if SVMUtil.tracefile:
        SVMUtil.tracefile.write(f'trace: {msg}\n'.encode(encoding='utf-8', errors='strict'))
        SVMUtil.tracefile.flush()


def adr(obj: gdb.Value) -> int:
    return int(obj) if obj.type.code == gdb.TYPE_CODE_PTR or obj.address is None else int(obj.address)


class SVMUtil:
    pretty_printer_name = "SubstrateVM"

    use_hlrep = True
    inference_limit = 10
    absolute_adr = False
    with_adr = False

    tracefile = None

    hub_field_name = "hub"
    compressed_ref_prefix = '_z_.'
    compressed_ref_re = re.compile(re.escape(compressed_ref_prefix) + r'(\w+\.)')

    selfref_parents = dict()
    selfref_cycles = set()
    selfref_check = True

    print_string_limit = 200
    print_static_fields = False
    complete_svar = False
    hlreps = dict()
    deopt_stub_adr = 0

    string_type = "java.lang.String"
    enum_type = "java.lang.Enum"
    object_type = "java.lang.Object"
    ccharpointer_type = "org.graalvm.nativeimage.c.type.CCharPointer"
    wrapper_types = ["Byte", "Short", "Integer", "Long", "Float", "Double", "Boolean", "Character"]

    @classmethod
    def strip_compression(cls, str_val: str) -> str:
        result = cls.compressed_ref_re.sub(r'\1', str_val)
        trace(f'<SVMUtil> - strip_compression({str_val}) = {result}')
        return result

    @classmethod
    def strip_package_information(cls, t: str) -> str:
        result = t.split('.')[-1]
        trace(f'<SVMUtil> - strip_package_information({t}) = {result}')
        return result

    @classmethod
    def is_compressed(cls, t: gdb.Type) -> bool:
        result = str(t).startswith(cls.compressed_ref_prefix)
        trace(f'<SVMUtil> - is_compressed({t.name}) = {str(result)}')
        return result

    @classmethod
    def adr_str(cls, obj: gdb.Value) -> str:
        if not cls.absolute_adr and cls.is_compressed(obj.type):
            result = f' @z({hex(adr(obj))})'
        else:
            result = f' @({cls.adr_repr(obj)})'
        trace(f'<SVMUtil> - adr_str({hex(adr(obj))}) = {result}')
        return result

    @classmethod
    def selfref_reset(cls, current_prompt: str = None) -> None:
        trace('<SVMUtil> - selfref_reset()')
        cls.selfref_parents.clear()
        cls.selfref_cycles.clear()

    @classmethod
    def adr_repr(cls, obj: gdb.Value) -> hex:
        result = hex(adr(obj.dereference())) if cls.is_compressed(obj.type) and adr(obj) > 0 else hex(adr(obj))
        trace(f'<SVMUtil> - adr_repr({adr(obj)}) = {str(result)}')
        return result

    @classmethod
    def is_selfref(cls, obj: gdb.Value) -> bool:
        result = (cls.selfref_check and
                  not cls.is_primitive(obj.type) and
                  cls.adr_repr(obj) in cls.selfref_cycles)
        trace(f'<SVMUtil> - is_selfref({hex(adr(obj))}) = {str(result)}')
        return result

    @classmethod
    def add_selfref(cls, parent: gdb.Value, child: gdb.Value) -> gdb.Value:
        # filter out null references
        if child.type.code == gdb.TYPE_CODE_PTR and int(child) == 0:
            return child

        trace(f'<SVMUtil> - add_selfref(parent={hex(adr(parent))}, child={hex(adr(child))})')
        if cls.selfref_check and not cls.is_primitive(child.type):
            (child_adr, parent_adr) = (cls.adr_repr(child), cls.adr_repr(parent))
            if cls.is_reachable(child_adr, parent_adr):
                trace(f' <add selfref {child_adr}>')
                cls.selfref_cycles.add(child_adr)
            else:
                trace(f' <add {child_adr} --> {parent_adr}>')
                cls.selfref_parents[child_adr] = parent_adr
        return child

    @classmethod
    def is_reachable(cls, start_node: hex, goal_node: hex) -> bool:
        trace(f'<SVMUtil> - is_reachable(start_node={start_node}, goal_node={goal_node}')
        depth = 0
        while True:
            if goal_node is None:
                return False
            if start_node == goal_node:
                return True
            goal_node = cls.selfref_parents.get(goal_node)
            depth += 1

    @classmethod
    def get_java_string(cls, obj: gdb.Value, string_value: bool = False) -> str:
        trace(f'<SVMUtil> - get_java_string({hex(adr(obj))})')
        try:
            coder = int(obj['coder'])
            trace('<SVMUtil> - get_java_string: coder = ' + str(coder))
            # From Java 9 on, value is byte[] with latin_1 or utf-16_le
            codec = {
                0: 'latin_1',
                1: 'utf-16_le',
            }.get(coder)
            bytes_per_char = 1
        except gdb.error:
            codec = 'utf-16'  # Java 8 has a char[] with utf-16
            bytes_per_char = 2

        value = obj["value"]
        value_content = value["data"]
        value_length = value["len"]

        string_data = bytearray()
        for index in range(min(SVMUtil.print_string_limit,
                               value_length) if string_value and SVMUtil.print_string_limit > 0 else value_length):
            mask = (1 << 8 * bytes_per_char) - 1
            code_unit = int(value_content[index] & mask)
            code_unit_as_bytes = code_unit.to_bytes(bytes_per_char, byteorder='little')
            string_data.extend(code_unit_as_bytes)
        result = string_data.decode(codec).replace("\x00", r"\0")
        if string_value and 0 < SVMUtil.print_string_limit < value_length:
            result += "..."

        trace(f'<SVMUtil> - get_java_string({hex(adr(obj))}) = {result}')
        return result

    @classmethod
    def get_hub(cls, obj: gdb.Value) -> gdb.Value:
        result = obj[cls.hub_field_name]
        trace(f'<SVMUtil> - get_hub({hex(adr(obj))}) = {hex(adr(result))}')
        return result

    @classmethod
    def get_rtt_name(cls, obj: gdb.Value) -> str:
        # check for interfaces and cast them to Object to make the hub accessible
        if obj.type.target().code == gdb.TYPE_CODE_UNION:
            obj = SVMUtil.cast_to(obj, SVMUtil.object_type)

        rtt_name = cls.get_java_string(cls.get_hub(obj)["name"])
        if rtt_name.startswith("["):
            array_dimension = rtt_name.count('[')
            if array_dimension > 0:
                rtt_name = rtt_name[array_dimension:]
            if rtt_name[0] == 'L':
                classname_end = rtt_name.find(';')
                rtt_name = rtt_name[1:classname_end]
            else:
                rtt_name = {
                    'Z': 'boolean',
                    'B': 'byte',
                    'C': 'char',
                    'D': 'double',
                    'F': 'float',
                    'I': 'int',
                    'J': 'long',
                    'S': 'short',
                }.get(rtt_name, rtt_name)
            rtt_name += ' '
            for _ in range(array_dimension):
                rtt_name += '[]'
        trace(f'<SVMUtil> - get_rtt_name({hex(adr(obj))}) = {rtt_name}')
        return rtt_name

    @classmethod
    def cast_to(cls, obj: gdb.Value, type_name: str) -> gdb.Value:
        # make sure to have a pointer, to be able to cast it
        if obj.type.code != gdb.TYPE_CODE_PTR:
            obj = obj.address

        trace(f'<SVMUtil> - cast_to({hex(obj)}, {type_name})')
        if cls.is_compressed(obj.type) and not type_name.startswith(cls.compressed_ref_prefix):
            type_name = cls.compressed_ref_prefix + type_name

        type_ptr = cls.get_type_ptr(type_name)

        trace(f'<SVMUtil> - cast_to({hex(obj)}, {type_name}) returned')
        return obj if str(type_ptr) == str(obj.type) else gdb.parse_and_eval(hex(obj)).cast(type_ptr)

    @classmethod
    def get_symbol_adr(cls, symbol: str) -> int:
        trace(f'<SVMUtil> - get_symbol_adr({symbol})')
        return gdb.parse_and_eval(symbol).address

    @classmethod
    def execout(cls, cmd: str) -> str:
        trace(f'<SVMUtil> - execout({cmd})')
        return gdb.execute(cmd, False, True)

    @classmethod
    def get_type_ptr(cls, java_type: str) -> gdb.Type:
        trace(f'<SVMUtil> - get_type_ptr({java_type})')
        return gdb.lookup_type(java_type).pointer()

    @classmethod
    def get_basic_type(cls, t: gdb.Type) -> gdb.Type:
        trace(f'<SVMUtil> - get_base_type({t})')
        while t.code == gdb.TYPE_CODE_PTR:
            t = t.target()
        return t

    @classmethod
    def is_primitive(cls, t: gdb.Type) -> bool:
        result = cls.get_basic_type(t).is_scalar
        trace(f'<SVMUtil> - is_primitive({t}) = {str(result)}')
        return result

    @classmethod
    def is_primitive_wrapper(cls, t: str) -> bool:
        result = any([(x in t) for x in cls.wrapper_types])
        trace(f'<SVMUtil> - is_primitive_wrapper({t}) = {str(result)}')
        return result

    @classmethod
    def is_enum_type(cls, type_name: str) -> bool:
        t = gdb.lookup_type(type_name)
        t = cls.get_base_class(t)
        return t.name == cls.enum_type

    @classmethod
    def get_base_class(cls, t: gdb.Type) -> gdb.Type:
        return gdb.lookup_type(cls.object_type) if t.name == cls.object_type else \
            next((f.type for f in t.fields() if f.is_base_class), gdb.lookup_type(cls.object_type))

    @classmethod
    def find_shared_parent_type(cls, common_type: list[str], t: gdb.Type) -> list[str]:
        if len(common_type) == 0:
            common_type.insert(0, t.name)
            while t.name != cls.object_type:
                t = cls.get_base_class(t)
                common_type.insert(0, t.name)
            return common_type
        else:
            while t.name != cls.object_type:
                if common_type.__contains__(t.name):
                    return common_type[:common_type.index(t.name) + 1]
                else:
                    t = cls.get_base_class(t)
            return [cls.object_type]

    @classmethod
    def get_all_fields(cls, t: gdb.Type) -> list[gdb.Field]:
        for f in cls.get_basic_type(t).fields():
            if f.is_base_class:
                yield from cls.get_all_fields(f.type)
            else:
                yield f

    @classmethod
    def get_all_member_function_names(cls, t: gdb.Type) -> set[str]:
        names = set()
        try:
            basic_type = cls.get_basic_type(t)
            members = SVMUtil.execout(f"ptype '{basic_type.name}'")
            for member in members.split('\n'):
                for part in member.split(' '):
                    if '(' in part:
                        names.add(part[:part.find('(')])
                        break
            for f in basic_type.fields():
                if f.is_base_class:
                    names = names.union(cls.get_all_member_function_names(f.type))
        except Exception as ex:
            trace(f'<SVMUtil> - get_all_member_function_names({t}) exception: {ex}')
        return names

    @classmethod
    def is_java_type(cls, t: gdb.Type) -> bool:
        # Check for hub field
        result = any(f.name == SVMUtil.hub_field_name for f in cls.get_all_fields(t))

        # interfaces have no hub, check if a corresponding '*.class' symbol exists
        if not result:
            basic_type = cls.get_basic_type(t)
            result = (basic_type.code == gdb.TYPE_CODE_UNION and
                      gdb.lookup_global_symbol(cls.strip_compression(basic_type.name) + '.class', gdb.SYMBOL_VAR_DOMAIN)
                      is not None)

        trace(f'<SVMUtil> - is_java_type({t}) = {result}')
        return result


class SVMPPString:
    def __init__(self, obj: gdb.Value, java: bool = True):
        trace(f'<SVMPPString> - __init__({hex(adr(obj))})')
        self.__obj = obj
        self.__java = java

    def to_string(self) -> str | gdb.Value:
        trace('<SVMPPString> - to_string')
        if self.__java:
            try:
                value = '"' + SVMUtil.get_java_string(self.__obj, True) + '"'
            except gdb.error:
                return SVMPPConst(None)
        else:
            value = str(self.__obj)
            value = value[value.index('"'):]
        if SVMUtil.with_adr:
            value += SVMUtil.adr_str(self.__obj)
        trace(f'<SVMPPString> - to_string = {value}')
        return value


class SVMPPArray:
    def __init__(self, obj: gdb.Value, length: int, array: gdb.Value = None):
        trace(f'<SVMPPArray> - __init__(obj={obj.type} @ {hex(adr(obj))}, length={str(length)}' +
              (')' if array is None else f', array={array.type} @ {hex(adr(array))})'))
        self.__obj = obj
        self.__selfref = SVMUtil.is_selfref(obj)
        self.__length = length
        self.__array = obj if array is None else array

    def display_hint(self) -> str:
        trace('<SVMPPArray> - display_hint = array')
        return 'array'

    def to_string(self) -> str | gdb.Value:
        trace('<SVMPPArray> - to_string')
        if SVMUtil.is_java_type(self.__obj.type):
            value = SVMUtil.strip_compression(SVMUtil.get_rtt_name(self.__obj))
            value = value.replace('[]', f'[{self.__length}]')
        else:
            value = str(self.__obj.type)
        if self.__selfref:
            value += ' = {...}'
        if SVMUtil.with_adr:
            value += SVMUtil.adr_str(self.__obj)
        trace(f'<SVMPPArray> - to_string = {value}')
        return value

    def __iter__(self):
        trace('<SVMPPArray> - __iter__')
        for i in range(self.__length):
            yield self.__array[i]

    def children(self) -> Iterable[Any]:
        trace('<SVMPPArray> - children')
        if self.__selfref:
            return
        for index, elem in enumerate(self):
            trace(f'<SVMPPArray> - children[{str(index)}]')
            yield str(index), SVMUtil.add_selfref(self.__obj, elem)


class SVMPPClass:
    def __init__(self, obj: gdb.Value):
        trace(f'<SVMPPClass> - __init__({obj.type} @ {hex(adr(obj))})')
        self.__obj = obj
        self.__selfref = SVMUtil.is_selfref(obj)

    def __getitem__(self, key: str) -> gdb.Value:
        trace(f'<SVMPPClass> - __get_item__({str(key)})')
        item = self.__obj[key]
        pp_item = gdb.default_visualizer(item)
        return item if pp_item is None else pp_item

    def to_string(self) -> str | gdb.Value:
        trace('<SVMPPClass> - to_string')
        try:
            if SVMUtil.is_java_type(self.__obj.type):
                result = SVMUtil.strip_compression(SVMUtil.get_rtt_name(self.__obj))
            else:
                result = "object" if self.__obj.type.name is None else self.__obj.type.name
            if self.__selfref:
                result += ' = {...}'
            if SVMUtil.with_adr:
                result += SVMUtil.adr_str(self.__obj)
            trace(f'<SVMPPClass> - to_string = {result}')
            return result
        except gdb.error as ex:
            trace("<SVMPPClass> - to_string error - SVMPPClass: " + str(ex))
            return 'object'

    def children(self) -> Iterable[Any]:
        trace('<SVMPPClass> - children (class field iterator)')
        if self.__selfref:
            return
        for f in SVMUtil.get_all_fields(self.__obj.type):
            trace(f'<SVMPPClass> - children: field "{f.name}"')
            if not SVMUtil.print_static_fields:
                try:
                    f.bitpos  # bitpos attribute is not available for static fields
                except:  # use bitpos access exception to skip static fields
                    continue
            if str(f.name) == SVMUtil.hub_field_name:
                continue
            yield str(f.name), SVMUtil.add_selfref(self.__obj, self.__obj[str(f.name)])


class SVMPPEnum:
    def __init__(self, obj: gdb.Value, type_name: str):
        trace(f'<SVMPPEnum> - __init__({hex(adr(obj))}, {type_name})')
        self.__obj = obj
        self.__type_name = type_name

    def to_string(self) -> str | gdb.Value:
        result = SVMUtil.get_java_string(self.__obj['name']) + f"({int(self.__obj['ordinal'])})"
        if SVMUtil.with_adr:
            result += SVMUtil.adr_str(self.__obj)
        trace(f'<SVMPPEnum> - to_string = {result}')
        return result


class SVMPPBoxedPrimitive:
    def __init__(self, obj: gdb.Value):
        trace(f'<SVMPPBoxedPrimitive> - __init__({obj.type} @ {hex(adr(obj))})')
        self.__obj = obj
        self.__value = obj['value']

    def to_string(self) -> str | gdb.Value:
        result = str(self.__value)
        if SVMUtil.with_adr:
            result += SVMUtil.adr_str(self.__obj)
        trace(f'<SVMPPBoxedPrimitive> - to_string = {result}')
        return result


class SVMPPConst:
    def __init__(self, val: str | None):
        trace('<SVMPPConst> - __init__')
        self.__val = val

    def to_string(self) -> str | gdb.Value:
        result = "null" if self.__val is None else self.__val
        trace(f'<SVMPPConst> - to_string = {result}')
        return result


class SVMPrettyPrinter(gdb.printing.PrettyPrinter):
    def __init__(self):
        super().__init__(SVMUtil.pretty_printer_name)

    def __call__(self, obj: gdb.Value):
        trace(f'<SVMPrettyPrinter> - __call__({obj.type} @ {hex(adr(obj))})')

        # Promote TYPEDEFs of runtime-compiled code to full types
        if obj.type.code == gdb.TYPE_CODE_PTR:
            target_type = obj.type.target()
            if target_type.code == gdb.TYPE_CODE_TYPEDEF:
                full_type = SVMUtil.get_type_ptr(str(target_type.name))
                obj = obj.cast(full_type)
                trace('<SVMPrettyPrinter> - applied typedef to full_type conversion')

        if not SVMUtil.is_primitive(obj.type) and SVMUtil.is_java_type(obj.type):
            # always use references to objects, to be able to cast them to their rtt
            if obj.type.code != gdb.TYPE_CODE_PTR:
                obj = obj.address

            # Filter out references to the null literal
            if int(obj) == 0:
                return SVMPPConst(None)

            # get runtime type information from the objects hub and cast the object
            rtt_name = SVMUtil.get_rtt_name(obj)
            rtt_name_uncompressed = SVMUtil.strip_compression(rtt_name)
            obj = SVMUtil.cast_to(obj, rtt_name)

            # filter for primitive wrappers
            if SVMUtil.is_primitive_wrapper(rtt_name_uncompressed):
                return SVMPPBoxedPrimitive(obj)

            # filter for strings
            if rtt_name_uncompressed == SVMUtil.string_type:
                return SVMPPString(obj)

            # filter for arrays
            if rtt_name.endswith("[]"):
                length = obj['len']
                array = obj['data']
                return SVMPPArray(obj, length, array)

            # filter for enum values
            if SVMUtil.is_enum_type(rtt_name_uncompressed):
                return SVMPPEnum(obj, rtt_name_uncompressed)

            # Any other Class ...
            if SVMUtil.use_hlrep:
                pp = make_high_level_object(obj, rtt_name_uncompressed)
            else:
                pp = SVMPPClass(obj)
            return pp

        # no java type
        else:
            basic_type = SVMUtil.get_basic_type(obj.type)
            if basic_type.code == gdb.TYPE_CODE_ARRAY:
                return SVMPPArray(obj, obj.type.range()[-1] + 1)
            elif basic_type.code == gdb.TYPE_CODE_TYPEDEF:
                if obj.type.name == SVMUtil.ccharpointer_type:
                    return SVMPPString(obj.cast(obj.type.target()), False)
                try:
                    obj = obj.dereference()
                    if obj.type.code == gdb.TYPE_CODE_STRUCT:
                        return SVMPPClass(obj)
                except gdb.error as err:
                    pass
            # handle primitive types
            elif SVMUtil.is_primitive(basic_type):
                # java chars have 2 bytes compared to 1 for c chars -> let gdb print c chars
                if basic_type.name == "char" and basic_type.sizeof == 2:
                    return SVMPPConst(repr(chr(obj)))
                elif basic_type.name == "byte":
                    return SVMPPConst(str(int(obj)))
                else:
                    return None
            return None


def HLRep(original_class):
    try:
        SVMUtil.hlreps[original_class.target_type] = original_class
    except Exception as e:
        trace(f'<@HLRep registration exception: {e}>')
    return original_class


@HLRep
class ArrayList:
    target_type = 'java.util.ArrayList'

    def __init__(self, pp: gdb.Value):
        trace(f'<ArrayList> - __init__({pp.type} @ {hex(adr(pp))})')
        self.size = pp['size']
        self.elementData = pp['elementData']
        self.obj = pp
        self.selfref = SVMUtil.is_selfref(self.obj)

    def to_string(self) -> str | gdb.Value:
        trace('<ArrayList> - to_string')
        res = 'java.util.ArrayList'
        if SVMUtil.inference_limit != 0:
            elem_type = self.infer_generic_types()
            if elem_type is not None:
                res += f'<{elem_type}>'
        res += f'({str(self.size)})'
        if self.selfref:
            res += ' = {...}'
        if SVMUtil.with_adr:
            res += SVMUtil.adr_str(self.obj)
        trace(f'<ArrayList> - to_string = {res}')
        return res

    def infer_generic_types(self) -> str:
        elem_type: list[str] = []
        n = 0

        for elem in self:
            n += 1
            if int(elem) != 0:  # check for null values
                elem_type = SVMUtil.find_shared_parent_type(elem_type, gdb.lookup_type(SVMUtil.get_rtt_name(elem)))
            if len(elem_type) == 1 or (0 <= SVMUtil.inference_limit <= n):
                break

        return None if len(elem_type) == 0 else SVMUtil.strip_package_information(elem_type[-1])

    def display_hint(self) -> str:
        trace('<ArrayList> - display_hint = array')
        return 'array'

    def __iter__(self) -> gdb.Value:
        trace('<ArrayList> - __iter__')
        for i in range(self.size):
            yield self.elementData["data"][i]

    def children(self) -> Iterable[Any]:
        trace(f'<ArrayList> - children({self.obj.type} @ {hex(adr(self.obj))})')
        for index, elem in enumerate(self):
            trace(f'<ArrayList> - children({self.obj.type} @ {hex(adr(self.obj))})[{str(index)}]')
            yield str(index), SVMUtil.add_selfref(self.obj, elem)


@HLRep
class HashMap:
    target_type = 'java.util.HashMap'

    def __init__(self, pp: gdb.Value):
        trace(f'<HashMap> - __init__({pp.type} @ {hex(adr(pp))})')
        self.size = pp['size']
        self.table = pp['table']
        self.obj = pp
        self.selfref = SVMUtil.is_selfref(self.obj)

    def to_string(self) -> str | gdb.Value:
        trace('<HashMap> - to_string')
        res = 'java.util.HashMap'
        if SVMUtil.inference_limit != 0:
            key_type, value_type = self.infer_generic_types()
            res += f"<{'?' if key_type is None else key_type}, {'?' if value_type is None else value_type}>"
        res += f'({str(self.size)})'
        if self.selfref:
            res += ' = {...}'
        if SVMUtil.with_adr:
            res += SVMUtil.adr_str(self.obj)
        trace(f'<HashMap> - to_string = {res}')
        return res

    def infer_generic_types(self) -> (str, str):
        key_type: list[str] = []
        value_type: list[str] = []
        n = 0

        for key, value in self:
            n += 1
            # if len(*_type) = 1 we could just infer the type java.lang.Object, ignore null values
            if len(key_type) != 1 and int(key) != 0:
                key_type = SVMUtil.find_shared_parent_type(key_type, gdb.lookup_type(SVMUtil.get_rtt_name(key)))
            if len(value_type) != 1 and int(value) != 0:
                value_type = SVMUtil.find_shared_parent_type(value_type, gdb.lookup_type(SVMUtil.get_rtt_name(value)))
            if (len(key_type) == 1 and len(value_type) == 1) or (0 <= SVMUtil.inference_limit <= n):
                break

        key_type_name = None if len(key_type) == 0 else SVMUtil.strip_package_information(key_type[-1])
        value_type_name = None if len(value_type) == 0 else SVMUtil.strip_package_information(value_type[-1])

        return key_type_name, value_type_name

    def display_hint(self) -> str:
        trace('<HashMap> - display_hint = array')
        return 'array'

    def __iter__(self) -> (gdb.Value, gdb.Value):
        trace('<HashMap> - __iter__')
        for i in range(self.table["len"]):
            obj = self.table["data"][i]
            while int(obj) != 0:
                yield obj['key'], obj['value']
                obj = obj['next']

    def children(self) -> Iterable[Any]:
        trace(f'<HashMap> - children({self.obj.type} @ {hex(adr(self.obj))})')
        for index, (key, value) in enumerate(self):
            trace(f'<HashMap> - children({self.obj.type} @ {hex(adr(self.obj))})[{str(index)}]')
            yield str(index), str(SVMUtil.add_selfref(self.obj, key)) + ": " + str(
                SVMUtil.add_selfref(self.obj, value)).replace("\n", "\n\t")


def make_high_level_object(pp: gdb.Value, rtt_name: str) -> gdb.Value:
    try:
        trace(f'try makeHighLevelObject for {rtt_name}')
        hl_rep_class = SVMUtil.hlreps[rtt_name]
        return hl_rep_class(pp)
    except Exception as e:
        trace(f'<makeHighLevelObject> exception: {e}')
    return SVMPPClass(pp)


class SVMCommandPrint(gdb.Command):
    """Use this command to enable/disable SVM pretty printing."""

    def __init__(self):
        super().__init__('svm-print', gdb.COMMAND_USER)

    def complete(self, text: str, word: str) -> list[str] | int:
        trace(f'<SVMCommandPrint> - complete({str(text)})')
        return [x for x in ['enable', 'disable'] if x.startswith(text)]

    def invoke(self, arg: str, from_tty: bool) -> None:
        trace(f'<SVMCommandPrint> - invoke({str(arg)})')
        if arg == '':
            print('svm-print is ' +
                  "enabled" if "[disabled]" in SVMUtil.execout("info pretty-printer .* SubstrateVM") else "disabled")
        elif arg == 'disable' or arg == 'enable':
            print(SVMUtil.execout(f"{arg} pretty-printer .* SubstrateVM"))


SVMCommandPrint()


class SVMCommandPrintStringLimit(gdb.Command):
    """Use this command to limit the number of characters in a string shown during pretty printing.
     (limits java strings + wrapper around gdb cstring limit, 0 for unlimited)"""

    def __init__(self):
        super().__init__('svm-print-string-limit', gdb.COMMAND_USER)
        # SVMUtil.print_string_limit = 200
        # SVMUtil.execout("set print characters 200")

    def invoke(self, arg: str, from_tty: bool) -> None:
        trace(f'<SVMCommandPrintStringLimit> - invoke({str(arg)})')
        if arg == '':
            print(f'svm-print-string-limit current value {SVMUtil.print_string_limit}')
        else:
            SVMUtil.print_string_limit = int(arg)
            SVMUtil.execout(f"set print characters {int(arg)}")


SVMCommandPrintStringLimit()


class SVMCommandUseHighLevel(gdb.Command):
    """Use this command to enable/disable SVM high level representations."""

    def __init__(self):
        super().__init__('svm-use-hlrep', gdb.COMMAND_USER)

    def complete(self, text: str, word: str) -> list[str] | int:
        trace(f'<SVMCommandUseHighLevel> - complete({str(text)})')
        return [x for x in ['enable', 'disable'] if x.startswith(text)]

    def invoke(self, arg: str, from_tty: bool) -> None:
        trace(f'<SVMCommandUseHighLevel> - invoke({str(arg)})')
        if arg == '':
            print('svm-use-hlrep is ' + "enabled" if SVMUtil.use_hlrep else "disabled")
        elif arg == 'disable':
            SVMUtil.use_hlrep = False
        else:
            SVMUtil.use_hlrep = True


SVMCommandUseHighLevel()


class SVMCommandInferGenericType(gdb.Command):
    """Use this command to set the limit of elements used to infer types for collections with generic type parameters.
    (0 for no Inference, -1 for Inference over all elements)"""

    def __init__(self):
        super().__init__('svm-infer-generics', gdb.COMMAND_USER)

    def complete(self, text: str, word: str) -> list[str] | int:
        trace(f'<SVMCommandInferGenericType> - complete({str(text)})')
        return ['disable'] if 'disable'.startswith(text) else []

    def invoke(self, arg: str, from_tty: bool) -> None:
        trace(f'<SVMCommandInferGenericType> - invoke({str(arg)})')
        if arg == '':
            print('svm-infer-generics ' +
                  f"considered elements = {SVMUtil.inference_limit}" if SVMUtil.inference_limit > 0 else "is disabled")
        elif arg == 'disable':
            SVMUtil.inference_limit = 0
        else:
            SVMUtil.inference_limit = int(arg)


SVMCommandInferGenericType()


class SVMCommandPrintAddresses(gdb.Command):
    """Use this command to enable/disable additionally printing the addresses."""

    def __init__(self):
        super().__init__('svm-print-address', gdb.COMMAND_USER)

    def complete(self, text: str, word: str) -> list[str] | int:
        trace(f'<SVMCommandPrintAddresses> - complete({str(text)})')
        return [x for x in ['enable', 'absolute', 'disable'] if x.startswith(text)]

    def invoke(self, arg: str, from_tty: bool) -> None:
        trace(f'<SVMCommandPrintAddresses> - invoke({str(arg)})')
        if arg == '':
            print('svm-print-address is ' + "enabled" if SVMUtil.with_adr else "disabled")
            print('with address mode ' + 'all absolute' if SVMUtil.absolute_adr else 'normal')
        elif arg == 'disable':
            SVMUtil.with_adr = False
        elif arg == 'absolute':
            SVMUtil.absolute_adr = True
            SVMUtil.with_adr = True
        else:
            SVMUtil.absolute_adr = False
            SVMUtil.with_adr = True


SVMCommandPrintAddresses()


class SVMCommandSelfref(gdb.Command):
    """Use this command to enable/disable cycle detection for pretty printing."""

    def __init__(self):
        super().__init__('svm-selfref-check', gdb.COMMAND_USER)

    def complete(self, text: str, word: str) -> list[str] | int:
        trace(f'<SVMCommandSelfref> - complete({str(text)})')
        return [x for x in ['enable', 'disable'] if x.startswith(text)]

    def invoke(self, arg: str, from_tty: bool) -> None:
        trace(f'<SVMCommandSelfref> - invoke({str(arg)})')
        if arg == '':
            print('svm-selfref-check is ' + 'enabled' if SVMUtil.selfref_check else 'disabled')
        elif arg == 'off' or arg == 'disable':
            SVMUtil.selfref_check = False
        else:
            SVMUtil.selfref_check = True
            SVMUtil.selfref_reset()


SVMCommandSelfref()


class SVMCommandPrintArrayLimit(gdb.Command):
    """Use this command to limit the number of array elements, map entries, and object fields shown during pretty printing.
     (wrapper for gdb 'print elements', 0 for unlimited)"""

    def __init__(self):
        super().__init__('svm-print-elements-limit', gdb.COMMAND_USER)
        SVMUtil.execout(f"set print elements 10")

    def invoke(self, arg: str, from_tty: bool) -> None:
        trace(f'<SVMCommandPrintArrayLimit> - invoke({str(arg)})')
        if arg == '':
            print(SVMUtil.execout("show print elements"))
        else:
            SVMUtil.execout(f"set print elements {int(arg)}")


SVMCommandPrintArrayLimit()


class SVMCommandPrintDepthLimit(gdb.Command):
    """Use this command to limit the depth of recursive pretty printing.
    (wrapper for gdb 'print max-depth', -1 for unlimited)"""

    def __init__(self):
        super().__init__('svm-print-depth-limit', gdb.COMMAND_USER)
        SVMUtil.execout("set print max-depth 3")

    def invoke(self, arg: str, from_tty: bool) -> None:
        trace(f'<SVMCommandPrintDepthLimit> - invoke({str(arg)})')
        if arg == '':
            print(SVMUtil.execout("show print max-depth"))
        else:
            SVMUtil.execout(f"set print max-depth {int(arg)}")


SVMCommandPrintDepthLimit()


class SVMCommandPrintStaticFields(gdb.Command):
    """Use this command to enable/disable printing of static field members."""

    def __init__(self):
        super().__init__('svm-print-static-fields', gdb.COMMAND_USER)

    def complete(self, text: str, word: str) -> list[str] | int:
        trace(f'<SVMCommandPrintStaticFields> - complete({str(text)})')
        return [x for x in ['enable', 'disable'] if x.startswith(text)]

    def invoke(self, arg: str, from_tty: bool) -> None:
        trace(f'<SVMCommandPrintStaticFields> - invoke({str(arg)})')
        if arg == '':
            print('svm-print-static-fields is ' + 'enabled' if SVMUtil.print_static_fields else 'disabled')
        elif arg == 'on' or arg == 'enable':
            SVMUtil.print_static_fields = True
        else:
            SVMUtil.print_static_fields = False


SVMCommandPrintStaticFields()


class SVMCommandCompleteStaticVariables(gdb.Command):
    """Use this command to enable/disable completion of static variables."""

    def __init__(self):
        super().__init__('svm-complete-static-variables', gdb.COMMAND_USER)

    def complete(self, text: str, word: str) -> list[str] | int:
        trace(f'<SVMCommandCompleteStaticVariables> - complete({str(text)})')
        return [x for x in ['enable', 'disable'] if x.startswith(text)]

    def invoke(self, arg: str, from_tty: bool) -> None:
        trace(f'<SVMCommandCompleteStaticVariables> - invoke({str(arg)})')
        if arg == '':
            print('svm-complete-static-variables is ' + 'enabled' if SVMUtil.complete_svar else 'disabled')
        elif arg == 'on' or arg == 'enable':
            SVMUtil.complete_svar = True
        else:
            SVMUtil.complete_svar = False


SVMCommandCompleteStaticVariables()


class SVMCommandCompleteDebugTrace(gdb.Command):
    """Use this command to enable/disable debug tracing for svmhelpers.py."""

    def __init__(self):
        super().__init__('svm-debug-tracing', gdb.COMMAND_USER)

    def complete(self, text: str, word: str) -> list[str] | int:
        trace(f'<SVMCommandCompleteDebugTrace> - complete({str(text)})')
        return [x for x in ['enable', 'disable'] if x.startswith(text)]

    def invoke(self, arg: str, from_tty: bool) -> None:
        trace(f'<SVMCommandCompleteDebugTrace> - invoke({str(arg)})')
        if arg == '':
            print('svm-debug-tracing is ' + 'enabled' if bool(SVMUtil.tracefile) else 'disabled')
        elif arg == 'on' or arg == 'enable':
            if not SVMUtil.tracefile:
                SVMUtil.tracefile = open('svmhelpers.trace.out', 'ab', 0)
        else:
            if SVMUtil.tracefile:
                SVMUtil.tracefile.close()
                SVMUtil.tracefile = None


SVMCommandCompleteDebugTrace()


class SVMCommandDebugPrettyPrinting(gdb.Command):
    """Use this command to start debugging pretty printing."""

    def __init__(self):
        super().__init__('pdb', gdb.COMMAND_DATA)

    def complete(self, text: str, word: str) -> list[str] | int:
        return gdb.COMPLETE_EXPRESSION

    def invoke(self, arg: str, from_tty: bool) -> None:
        trace(f'<SVMCommandDebugPrettyPrinting> - invoke({str(arg)})')
        command = "gdb.execute('print {}')".format(arg.replace("'", "\\'"))
        pdb.run(command)


SVMCommandDebugPrettyPrinting()


class SVMCommandPrint(gdb.Command):
    """Use this command for printing with awareness for java values.
    This command shadows the alias 'p' for GDBs built-in print command.
    If the expression contains a java value, it is evaluated as such, otherwise GDBs default print command is used"""

    def __init__(self):
        super().__init__('p', gdb.COMMAND_DATA)

    @staticmethod
    def find_closing_bracket(text: str) -> int:
        offset = 0
        sub_arrays = text.count('[')
        for i in range(sub_arrays):
            offset = text.find(']', offset) + 1
        return text.find(']', offset)

    @staticmethod
    def extract_context(context: str) -> str:
        context_split = context.rfind('[')
        while context_split != -1 and SVMCommandPrint.find_closing_bracket(context[context_split+1:]) != -1:
            context_split = context[:context_split].rfind('[')
        return context[context_split+1:]

    @staticmethod
    def extend_cast_to_rtt(obj: gdb.Value, full_name_extended: str) -> tuple[gdb.Value, str]:
        static_type = SVMUtil.get_basic_type(obj.type)
        rtt_name = SVMUtil.get_rtt_name(obj)
        obj = SVMUtil.cast_to(obj, rtt_name)
        rtt = SVMUtil.get_basic_type(obj.type)

        if static_type.name != rtt.name:
            full_name_extended = f"(('{rtt.name}' *)({full_name_extended}))"

        return obj, full_name_extended

    @staticmethod
    def reconstruct_field_access(text: str, context: tuple[gdb.Value, str, str] = (None, "", "")) -> tuple[gdb.Value, str, str]:
        obj, full_name, full_name_extended = context
        for field in text.split('.'):
            if obj is None:
                obj = gdb.parse_and_eval(field)
            else:
                obj = obj[field]
                full_name += '.'
                full_name_extended += '.'

            full_name += field
            full_name_extended += field
            if not SVMUtil.is_primitive(obj.type):
                obj, full_name_extended = SVMCommandPrint.extend_cast_to_rtt(obj, full_name_extended)

        return obj, full_name, full_name_extended

    def reconstruct_object(self, text: str) -> tuple[gdb.Value, str, str]:
        # not cached because what if we never complete this command -> may result in faulty data
        # e.g. user inputs 'p arr[2]' (arr is a java array) and autocompletes but never completes the command ->
        #      full_name_extended is set to 'arr.data[2]'
        #      then later the user inputs 'p arr[2]' (arr is c arr) and completes the command ->
        #      although the same full_name, full_name_extended should be different, but we still have 'arr.data[2]'
        obj = None  # for storing the currently reconstructed object
        full_name = ""  # for storing the fully qualified name of the reconstructed object
        full_name_extended = ""  # extended fully qualified name to handle java data

        # TODO: function calls

        if '[' not in text:
            obj, full_name, full_name_extended = self.reconstruct_field_access(text)
        else:
            field_access_str = text[:text.find('[')]
            rest = text[text.find('['):]

            if field_access_str == '':
                raise RuntimeError(f"Invalid start of expression: {field_access_str}")

            obj = gdb.parse_and_eval(field_access_str)
            full_name = field_access_str
            full_name_extended = field_access_str

            while rest.startswith('[') or rest.startswith('.'):
                if rest[0] == '[':
                    # handle array access
                    if gdb.default_visualizer(obj).__class__.__name__ != "SVMPPArray":
                        raise RuntimeError(f"{full_name} is not an array")

                    closing_bracket = self.find_closing_bracket(rest[1:]) + 1
                    if closing_bracket == 0:
                        raise RuntimeError(f"Unfinished array access at: {full_name}")

                    array_index_string = rest[1:closing_bracket]
                    (i_obj, i_full_name, i_full_name_extended) = self.reconstruct_object(array_index_string)
                    if SVMUtil.is_primitive(i_obj.type):
                        index = int(i_obj)
                    elif gdb.default_visualizer(i_obj).__class__.__name__ == "SVMPPBoxedPrimitive":
                        index = int(i_obj['value'])
                        i_full_name_extended = f'({i_full_name_extended}).value'
                    else:
                        raise RuntimeError(f"array index must be a primitive value but is of type: {SVMUtil.get_basic_type(i_obj.type).name}")  # only support primitives as array index
                    if SVMUtil.is_java_type(obj.type):
                        full_name_extended += ".data"
                        obj = obj['data']
                    full_name += f'[{i_full_name}]'
                    full_name_extended += f'[{index}]'
                    obj = obj[index]
                    rest = rest[closing_bracket+1:]
                    obj, full_name_extended = self.extend_cast_to_rtt(obj, full_name_extended)
                else:
                    # handle field access
                    if '[' in rest:
                        field_access_str = rest[1:rest.find('[')]
                        rest = rest[rest.find('['):]
                    else:
                        field_access_str = rest[1:]
                        rest = ''

                    obj, full_name, full_name_extended = self.reconstruct_field_access(field_access_str, (obj, full_name, full_name_extended))

            if rest != '':
                raise RuntimeError(f"Invalid expression at: {full_name}")

        return obj, full_name, full_name_extended

    def complete(self, text: str, word: str) -> list[str]:
        trace(f'<SVMCommandPrint> - complete({text}, {word})')
        if text.rfind(']') > text.rfind('.'):
            return gdb.COMPLETE_NONE  # no completion after closed brackets
        elif text.rfind('[') > text.rfind('.'):
            # array value completion
            prefix, _, last_part = text.rpartition('[')
            context = self.extract_context(prefix)
            try:
                obj, full_name, full_name_extended = self.reconstruct_object(context)
            except RuntimeError as ex:
                print(f"\n{ex}\n")
                return gdb.COMPLETE_NONE
            if SVMUtil.is_java_type(obj.type) and (last_part == '' or last_part.isnumeric()):
                index = 0 if last_part == '' else int(last_part)
                length = int(obj['len'])
                complete = []
                if index < length:
                    complete.append(f'{index}]')
                    if index + 1 < length:
                        complete.append(f'{index + 1}]')
                        if index + 2 < length:
                            complete.append(f'{length - 1}]')
                return complete
            else:
                return gdb.COMPLETE_EXPRESSION
        elif text.rfind('.') > text.rfind('['):
            # field access completion
            prefix, _, last_part = text.rpartition('.')
            context = self.extract_context(prefix)
            try:
                obj, full_name, full_name_extended = self.reconstruct_object(context)
            except RuntimeError as ex:
                print(f"\n{ex}\n")
                return gdb.COMPLETE_NONE
            complete_set = (set(f.name for f in SVMUtil.get_all_fields(obj.type))
                            .union(SVMUtil.get_all_member_function_names(obj.type)))
            return [c for c in complete_set if c.startswith(last_part)]
        else:
            return gdb.COMPLETE_EXPRESSION  # no field access or array access in text

    def invoke(self, arg: str, from_tty: bool) -> None:
        trace(f'<SVMCommandPrint> - invoke({arg}, {str(from_tty)})')
        try:
            obj, full_name, full_name_extended = self.reconstruct_object(arg)
        except RuntimeError as ex:
            print(f"\n{ex}\n")
            return
        # let gdb reevaluate this for output coloring and storing into history
        gdb.execute(f'print {full_name_extended}', False, False)


SVMCommandPrint()


class SVMFrameUnwinder(gdb.unwinder.Unwinder):
    AMD64_RBP = 6
    AMD64_RSP = 7
    AMD64_RIP = 16

    def __init__(self):
        super().__init__('SubstrateVM FrameUnwinder')
        self.stack_type = gdb.lookup_type('long')
        self.deopt_frame_type = gdb.lookup_type('com.oracle.svm.core.deopt.DeoptimizedFrame')

    def __call__(self, pending_frame):
        if SVMUtil.deopt_stub_adr == 0:
            # find deopt stub after its properly loaded
            SVMUtil.deopt_stub_adr = gdb.lookup_global_symbol('com.oracle.svm.core.deopt.Deoptimizer::deoptStub',
                                                              gdb.SYMBOL_VAR_DOMAIN).value().address

        try:
            rsp = pending_frame.read_register('sp')
            rip = pending_frame.read_register('pc')
            if int(rip) == SVMUtil.deopt_stub_adr:
                print("found deopt stub")
                deopt_frame_stack_slot = rsp.cast(self.stack_type.pointer()).dereference()
                deopt_frame = deopt_frame_stack_slot.cast(self.deopt_frame_type.pointer())
                source_frame_size = deopt_frame['sourceTotalFrameSize']
                # Now find the register-values for the caller frame
                unwind_info = pending_frame.create_unwind_info(gdb.unwinder.FrameId(rsp, rip))
                caller_rsp = rsp + int(source_frame_size)
                unwind_info.add_saved_register(self.AMD64_RSP, gdb.Value(caller_rsp))
                caller_rip = gdb.Value(caller_rsp - 8).cast(self.stack_type.pointer()).dereference()
                unwind_info.add_saved_register(self.AMD64_RIP, gdb.Value(caller_rip))
                return unwind_info
        except Exception as e:
            print(e)
            # Fallback to default frame unwinding via debug_frame (dwarf)

        return None


class SVMFrameFilter():
    def __init__(self):
        self.name = "SubstrateVM FrameFilter"
        self.priority = 100
        self.enabled = True

    def filter(self, frame_iter):
        for frame in frame_iter:
            frame = frame.inferior_frame()
            if SVMUtil.deopt_stub_adr and frame.pc() == SVMUtil.deopt_stub_adr:
                yield SVMFrameDeopt(frame)
            else:
                yield SVMFrame(frame)


class SVMFrame(FrameDecorator):
    def function(self):
        frame = self.inferior_frame()
        if not frame.name():
            return 'Unknown Frame at ' + hex(int(frame.read_register('sp')))
        func_name = str(frame.name().split('(')[0])
        if frame.type() == gdb.INLINE_FRAME:
            func_name = '<-- ' + func_name

        filename = self.filename()
        if filename:
            line = self.line()
            if line is None:
                line = 0
            eclipse_filename = '(' + os.path.basename(filename) + ':' + str(line) + ')'
        else:
            eclipse_filename = ''

        return func_name + eclipse_filename


class SVMFrameDeopt(SVMFrame):
    def function(self):
        return '[DEOPT FRAMES ...]'

    def frame_args(self):
        return None

    def frame_locals(self):
        return None


try:
    svm_objfile = gdb.objfiles()[0]
    print("SVM OBJECT FILE:", svm_objfile)
    # Only if we have an objfile and an SVM specific symbol we consider this an SVM objfile
    if svm_objfile and SVMUtil.get_symbol_adr('graal_create_isolate'):
        try:
            svminitfile = os.path.expandvars('${SVMGDBINITFILE}')
            exec(open(svminitfile).read())
            trace(f'successfully processed svminitfile: {svminitfile}')
        except Exception as e:
            trace(f'<exception in svminitfile execution: {e}>')

        gdb.printing.register_pretty_printer(svm_objfile, SVMPrettyPrinter())
        gdb.prompt_hook = SVMUtil.selfref_reset

        # deopt stub points to the wrong address at first -> set dummy value to fill later (0 from SVMUtil)
        deopt_stub_available = gdb.lookup_global_symbol('com.oracle.svm.core.deopt.Deoptimizer::deoptStub',
                                                        gdb.SYMBOL_VAR_DOMAIN)

        if deopt_stub_available:
            SVMUtil.frame_unwinder = SVMFrameUnwinder()
            gdb.unwinder.register_unwinder(svm_objfile, SVMUtil.frame_unwinder)

        SVMUtil.frame_filter = SVMFrameFilter()
        svm_objfile.frame_filters[SVMUtil.frame_filter.name] = SVMUtil.frame_filter
    else:
        print('Warning: Load ' + os.path.basename(__file__) + ' only in the context of an SVM objfile')


except Exception as e:
    print(f'<exception in svmhelper initialization: {e}>')
