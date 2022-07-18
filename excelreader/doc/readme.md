## example
 

```json

{
    "job": {
        "setting": {
            "speed": {
                "byte":10485760
            },
            "errorLimit": {
                "record": 0,
                "percentage": 0.02
            }
        },
        "content": [
            {
                "reader": {
		  "name": "excelreader",
		  "parameter": {
		    "path": ['/tmp/abc.xlsx'],
		    "header": false,
		    "skipRows": 0,
		    "ignoreColumns":[2,5]
		  }
                },
                "writer": {
                    "name": "streamwriter",
                    "parameter": {
                        "print": true,
                        "encoding": "UTF-8"
                    }
                }
            }
        ]
    }
}


```
